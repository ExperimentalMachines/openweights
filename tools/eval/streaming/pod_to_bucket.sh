#!/bin/sh
# Send a file from a GPU box with a single-object upload capability, not an account token.
#
#   pod_to_bucket.sh "<ssh command for the box>" <path on the box> <bucket> <object name>
#
# This machine's uplink is a third of a megabyte a second when two uploads share it, and
# a Test Lab run needs a gigabyte of model. The box has the bandwidth and must not have
# the account. So the upload session is opened here, with this machine's token, and only
# the session URI crosses to the box: it authorises writing that one object and nothing
# else, and it expires in a week.
set -eu
SSH=${1:?}; SRC=${2:?}; BUCKET=${3:?}; NAME=${4:?}
TOKEN=$(gcloud auth print-access-token ${ACCOUNT:+--account "$ACCOUNT"})
OBJECT=$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$NAME")
# Feed bearer credentials over stdin, not command arguments visible in process listings.
HEADERS=$(printf 'header = "Authorization: Bearer %s"\n' "$TOKEN" |
  curl --config - --fail --silent --show-error -i -X POST -H "Content-Type: application/json" \
    -H "X-Upload-Content-Type: application/octet-stream" -d '{}' \
    "https://storage.googleapis.com/upload/storage/v1/b/$BUCKET/o?uploadType=resumable&name=$OBJECT")
URI=$(printf '%s\n' "$HEADERS" | grep -i '^location:' | cut -d' ' -f2 | tr -d '\r')
unset TOKEN
[ -n "$URI" ] || { echo "no upload session for $NAME" >&2; exit 1; }
QUOTED_SRC=$(python3 -c 'import shlex, sys; print(shlex.quote(sys.argv[1]))' "$SRC")
# The session URI is itself a bearer capability. Keep it out of both local and remote argv.
printf 'url = "%s"\n' "$URI" |
  $SSH "curl --config - --fail --silent --show-error -o /dev/null -w '%{http_code} %{size_upload} bytes\n' -X PUT --upload-file $QUOTED_SRC"
