# A Device Streaming phone over plain adb

Run from `tools/eval/streaming/` with an authenticated gcloud account and a selected project.
`uv run --with` keeps the Python dependency environment outside the checkout:

    uv run --with google-cloud-devicestreaming python ds_bridge.py reserve pa3q 36 --ttl 3600
    uv run --with google-cloud-devicestreaming python ds_bridge.py serve <session> --port 5599
    # In another terminal:
    adb connect localhost:5599
    uv run --with google-cloud-devicestreaming python ds_bridge.py cancel <session>

`GCLOUD` can select a gcloud executable outside `PATH`. The bridge listens only on
`127.0.0.1`; other local processes can use that unauthenticated adb port. Closing the bridge
does not cancel the cloud reservation. Cancel explicitly when finished.

Model ids are Test Lab's (`gcloud firebase test android models list`; the ones that say
`directAccessSupported`). The project is gcloud's current one and needs the Device
Streaming API enabled. Check the project's current pricing and quota before reserving:
these are billable cloud actions, not offline tests. Observed on 2026-09-18: a session
asked for three hours expired after one, `extend` reached three hours but no further, and
the project had a 200-minute monthly quota. Session expiry removes its files. Reserve
when the files to test are ready, not before.
Measured 2026-09-17: a 50 MB push arrives intact at about 7 MB/s, and the phone reaches
Hugging Face on its own network.

These utilities support the app's actual instrumentation runners
(`../bench/run_decisions.sh`, `../bench/run_decisions_ftl_local.sh` and
`../bench/run_ftl_local.sh`). They do not make a model export or publish a model.
Model-side Qualcomm/Firebase benchmark archives live outside this repository under
`~/ow-models/benchmarks/`; app integration captures stay under `../results/`.

`pod_to_bucket.sh` is an optional transfer helper for those Test Lab runners. It creates
a resumable Cloud Storage upload locally and gives the remote box only its single-object
upload URI, not the account access token. That URI is still a bearer capability: do not
log or share it, and use only a trusted remote host. The helper sends credentials on stdin
rather than in process arguments and fails on HTTP errors. It requires local Python 3,
gcloud and curl, and remote curl. Do not run it as part of offline validation.
