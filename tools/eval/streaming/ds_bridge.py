#!/usr/bin/env python3
"""A Firebase Device Streaming phone as a plain adb device, without Android Studio.

  ds_bridge.py reserve pa3q 36 [--ttl 3600]      -> prints the session name
  ds_bridge.py serve <session> [--port 5599]     -> then: adb connect localhost:5599
  ds_bridge.py extend <session> --ttl 3600
  ds_bridge.py cancel <session>
  ds_bridge.py list

The Device Streaming API (devicestreaming.googleapis.com) hands out a physical phone and
one bidirectional gRPC stream, AdbConnect, that multiplexes adb *services* (Open, data,
Close). adb on this machine wants to speak the adb *transport* protocol (CNXN, OPEN,
OKAY, WRTE, CLSE) to something on a TCP port. This file is the adapter between the two:
it plays the device end of the transport protocol on localhost and turns every OPEN into
an Open on the gRPC stream. Android Studio does the same thing inside the IDE.

Credentials come from `gcloud auth print-access-token`, because this machine has no
application-default credentials and the token must never be written down.
"""
import argparse
import os
import queue
import socket
import struct
import subprocess
import sys
import threading
import time

# gRPC's fork handlers deadlock when the token is fetched by a subprocess from one of its
# threads: every call after the first then ends in DeadlineExceeded.
os.environ.setdefault("GRPC_ENABLE_FORK_SUPPORT", "0")

import google.auth.credentials  # noqa: E402
from google.cloud import devicestreaming_v1 as ds
from google.protobuf import duration_pb2, field_mask_pb2

GCLOUD_CANDIDATES = [os.environ.get("GCLOUD", "gcloud")]

CNXN, AUTH, OPEN, OKAY, CLSE, WRTE = (
    0x4E584E43, 0x48545541, 0x4E45504F, 0x59414B4F, 0x45534C43, 0x45545257,
)
# The first version at which the peer may leave the checksum at zero.
VERSION = 0x01000001
MAXDATA = 256 * 1024
# delayed_ack changes the OKAY accounting; the bridge acknowledges every write at once.
DROPPED_FEATURES = {"delayed_ack"}
DEFAULT_BANNER = (
    b"device::ro.product.name=streamed;ro.product.model=streamed;ro.product.device=streamed;"
    b"features=shell_v2,cmd,stat_v2,ls_v2,fixed_push_mkdir,apex,abb,fixed_push_symlink_timestamp,"
    b"abb_exec,sendrecv_v2,sendrecv_v2_brotli,sendrecv_v2_lz4,sendrecv_v2_zstd,sendrecv_v2_dry_run_send"
)


def gcloud(*args):
    for exe in GCLOUD_CANDIDATES:
        try:
            return subprocess.run([exe, *args], check=True, capture_output=True, text=True).stdout.strip()
        except FileNotFoundError:
            continue
    raise SystemExit("gcloud not found")


class GcloudCredentials(google.auth.credentials.Credentials):
    """The token is fetched before gRPC starts and renewed by a timer thread, never from
    inside a gRPC callback."""

    def __init__(self):
        super().__init__()
        self._renew()

    def _renew(self):
        self.token = gcloud("auth", "print-access-token")
        t = threading.Timer(1500, self._renew)
        t.daemon = True
        t.start()

    def refresh(self, request):
        pass

    @property
    def expired(self):
        return False

    @property
    def valid(self):
        return True


def client():
    # An idle stream is dropped after about a minute ("Stream removed ... Operation timed
    # out"). HTTP/2 pings every 20 s kept it open for a while and then the server answered
    # GOAWAY "too_many_pings" and dropped it anyway, so what keeps a stream alive is real
    # traffic: Connection.heartbeat opens a no-op adb service twice a minute.
    from google.cloud.devicestreaming_v1.services.direct_access_service.transports import (
        DirectAccessServiceGrpcTransport as Transport,
    )

    channel = Transport.create_channel(
        "devicestreaming.googleapis.com:443",
        credentials=GcloudCredentials(),
        options=[
            ("grpc.keepalive_time_ms", 300000),
            ("grpc.keepalive_timeout_ms", 20000),
            ("grpc.max_receive_message_length", -1),
            ("grpc.max_send_message_length", -1),
        ],
    )
    return ds.DirectAccessServiceClient(transport=Transport(channel=channel))


def project():
    return gcloud("config", "get-value", "project")


def reserve(args):
    c = client()
    session = ds.DeviceSession(
        android_device=ds.AndroidDevice(android_model_id=args.model, android_version_id=args.version),
        ttl=duration_pb2.Duration(seconds=args.ttl),
    )
    s = c.create_device_session(parent=f"projects/{project()}", device_session=session)
    print(s.name, s.state.name, flush=True)
    while s.state.name in ("SESSION_STATE_UNSPECIFIED", "REQUESTED", "PENDING"):
        time.sleep(3)
        s = c.get_device_session(name=s.name)
        print(s.name, s.state.name, flush=True)


def extend(args):
    c = client()
    s = ds.DeviceSession(name=args.session, ttl=duration_pb2.Duration(seconds=args.ttl))
    out = c.update_device_session(device_session=s, update_mask=field_mask_pb2.FieldMask(paths=["ttl"]))
    print(out.name, out.state.name, out.expire_time)


def cancel(args):
    client().cancel_device_session(request=ds.CancelDeviceSessionRequest(name=args.session))
    print("cancelled", args.session)


def list_sessions(args):
    for s in client().list_device_sessions(parent=f"projects/{project()}"):
        if args.all or s.state.name in ("ACTIVE", "PENDING", "REQUESTED"):
            print(s.name, s.state.name, s.android_device.android_model_id, s.expire_time)


def pack(cmd, arg0, arg1, data=b""):
    return struct.pack("<6I", cmd, arg0, arg1, len(data), 0, cmd ^ 0xFFFFFFFF) + data


def read_exact(sock, n):
    buf = bytearray()
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("adb closed")
        buf += chunk
    return bytes(buf)


class Connection:
    """One `adb connect`: one socket, one gRPC stream, many adb streams."""

    def __init__(self, sock, session, verbose, api_client):
        self.sock = sock
        self.session = session
        self.verbose = verbose
        self.api_client = api_client
        self.out = queue.Queue()          # AdbMessage to the phone; None ends the stream
        self.send_lock = threading.Lock()
        self.lock = threading.Lock()
        self.host_id = {}                 # our stream id -> adb's local id
        self.pending = {}                 # our stream id -> bytes chunks waiting for adb's OKAY
        self.awaiting = set()             # our stream ids with a WRTE adb has not acknowledged
        self.closing = set()              # the phone closed; CLSE goes out after the last chunk
        self.next_id = 1
        self.banner = None
        self.banner_ready = threading.Event()
        self.done = threading.Event()

    def log(self, *a):
        if self.verbose:
            print(*a, file=sys.stderr, flush=True)

    def send(self, cmd, arg0, arg1, data=b""):
        with self.send_lock:
            self.sock.sendall(pack(cmd, arg0, arg1, data))

    def requests(self):
        while True:
            m = self.out.get()
            if m is None:
                return
            yield m

    def pump(self, sid):
        """Send the next waiting chunk of one stream if adb has acknowledged the last."""
        with self.lock:
            if sid in self.awaiting or sid not in self.host_id:
                return
            chunks = self.pending.get(sid)
            if chunks:
                data = chunks.pop(0)
                self.awaiting.add(sid)
                hid = self.host_id[sid]
            elif sid in self.closing:
                hid = self.host_id.pop(sid)
                self.pending.pop(sid, None)
                self.closing.discard(sid)
                data = None
            else:
                return
        if data is None:
            self.send(CLSE, sid, hid)
        else:
            self.send(WRTE, sid, hid, data)

    def from_phone(self, responses):
        try:
            for m in responses:
                m = ds.DeviceMessage.pb(m)
                kind = m.WhichOneof("contents")
                if kind == "status_update":
                    u = m.status_update
                    self.log("status", u.state, dict(u.properties))
                    if u.state == 1 and not self.banner_ready.is_set():
                        feats = ",".join(f for f in u.features.split(",") if f and f not in DROPPED_FEATURES)
                        props = ";".join(f"{k}={v}" for k, v in u.properties.items())
                        self.banner = f"device::{props};features={feats}".encode()
                        self.banner_ready.set()
                elif kind == "stream_status":
                    sid = m.stream_status.stream_id
                    hid = self.host_id.get(sid)
                    if hid is None:
                        continue
                    if m.stream_status.WhichOneof("status") == "okay":
                        self.send(OKAY, sid, hid)
                    else:
                        self.log("open failed", sid, m.stream_status.fail.reason)
                        with self.lock:
                            self.host_id.pop(sid, None)
                        self.send(CLSE, 0, hid)
                elif kind == "stream_data":
                    sid = m.stream_data.stream_id
                    if m.stream_data.WhichOneof("contents") == "close":
                        with self.lock:
                            if sid not in self.host_id:
                                continue
                            self.closing.add(sid)
                    else:
                        data = m.stream_data.data
                        with self.lock:
                            if sid not in self.host_id:
                                continue
                            q = self.pending.setdefault(sid, [])
                            for i in range(0, len(data), MAXDATA):
                                q.append(data[i:i + MAXDATA])
                    self.pump(sid)
        except Exception as e:  # the gRPC stream ended; adb must see the device go away
            self.log("grpc ended:", repr(e))
        finally:
            self.banner_ready.set()
            try:
                self.sock.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass

    def heartbeat(self):
        """A `shell:true` every 25 s while adb is connected; its replies carry an id adb
        never opened, which from_phone drops."""
        while not self.done.wait(25):
            with self.lock:
                sid = self.next_id
                self.next_id += 1
            self.out.put(ds.AdbMessage(open_=ds.Open(stream_id=sid, service="shell:true")))

    def run(self):
        c = self.api_client
        threading.Thread(target=self.heartbeat, daemon=True).start()
        # The raw stub, not the client's wrapper: the wrapper waits for the first reply
        # before returning, and the phone says nothing until adb has asked for something.
        # Without the project header the stream ends in "No project ID provided".
        responses = c.transport.adb_connect(
            self.requests(),
            metadata=[
                ("x-omnilab-session-name", self.session),
                ("x-goog-user-project", self.session.split("/")[1]),
            ],
        )
        threading.Thread(target=self.from_phone, args=(responses,), daemon=True).start()
        try:
            while True:
                cmd, arg0, arg1, n, _, _ = struct.unpack("<6I", read_exact(self.sock, 24))
                data = read_exact(self.sock, n) if n else b""
                if cmd == CNXN:
                    # The service was seen to send no status until a stream is opened, so
                    # the banner cannot wait for one; these are a current adbd's features.
                    self.banner_ready.wait(2)
                    self.send(CNXN, VERSION, MAXDATA, self.banner or DEFAULT_BANNER)
                elif cmd == OPEN:
                    with self.lock:
                        sid = self.next_id
                        self.next_id += 1
                        self.host_id[sid] = arg0
                    service = data.rstrip(b"\0").decode("utf-8", "replace")
                    # Shell services may contain bearer tokens or private command arguments.
                    self.log("open", sid, service.partition(":")[0])
                    self.out.put(ds.AdbMessage(open_=ds.Open(stream_id=sid, service=service)))
                elif cmd == WRTE:
                    sid = arg1
                    self.out.put(ds.AdbMessage(stream_data=ds.StreamData(stream_id=sid, data=data)))
                    self.send(OKAY, sid, arg0)
                elif cmd == OKAY:
                    sid = arg1
                    with self.lock:
                        self.awaiting.discard(sid)
                    self.pump(sid)
                elif cmd == CLSE:
                    sid = arg1
                    with self.lock:
                        known = self.host_id.pop(sid, None)
                        self.pending.pop(sid, None)
                        self.awaiting.discard(sid)
                        self.closing.discard(sid)
                    if known is not None:
                        self.out.put(ds.AdbMessage(stream_data=ds.StreamData(stream_id=sid, close=ds.Close())))
                else:
                    self.log("ignored command", hex(cmd))
        except (ConnectionError, OSError) as e:
            self.log("adb side ended:", repr(e))
        finally:
            self.done.set()
            self.out.put(None)
            responses.cancel()
            self.sock.close()


def serve(args):
    # Reconnects share one credential renewal timer and transport, not one leaked
    # timer thread per adb connection.
    api_client = client()
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", args.port))
    srv.listen(4)
    print(f"listening; run: adb connect localhost:{args.port}", flush=True)
    while True:
        sock, _ = srv.accept()
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        threading.Thread(target=Connection(sock, args.session, args.verbose, api_client).run, daemon=True).start()


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("reserve"); r.add_argument("model"); r.add_argument("version"); r.add_argument("--ttl", type=int, default=900); r.set_defaults(f=reserve)
    s = sub.add_parser("serve"); s.add_argument("session"); s.add_argument("--port", type=int, default=5599); s.add_argument("-v", "--verbose", action="store_true"); s.set_defaults(f=serve)
    e = sub.add_parser("extend"); e.add_argument("session"); e.add_argument("--ttl", type=int, required=True); e.set_defaults(f=extend)
    x = sub.add_parser("cancel"); x.add_argument("session"); x.set_defaults(f=cancel)
    l = sub.add_parser("list"); l.add_argument("--all", action="store_true"); l.set_defaults(f=list_sessions)
    a = p.parse_args()
    a.f(a)


if __name__ == "__main__":
    main()
