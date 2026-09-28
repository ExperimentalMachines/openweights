#!/bin/sh
# Puts what the release workflow needs into the repository's secrets, from the files on the
# machine that cuts releases. Nothing is printed: each value goes from its file straight into
# `gh secret set` on stdin, so it never lands in the terminal, the shell history or a log.
#
#   tools/release/set_play_secrets.sh [path/to/play-service-account.json]
#     GEMINI_API_KEY=... in the environment also sets that secret, for drafted notes
#
# The upload key and its passwords come from keystore.properties and the keystore it names.
# Run it again whenever one of them changes; a secret that is set again is replaced.
set -eu
REPO=ExperimentalMachines/openweights
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
PROPS="$ROOT/keystore.properties"

[ -f "$PROPS" ] || { echo "No keystore.properties in $ROOT; this runs on the machine that signs releases." >&2; exit 1; }

# key=value or key: value, as java.util.Properties reads them, without the line ending.
prop() {
  sed -n "s/^[[:space:]]*$1[[:space:]]*[=:][[:space:]]*//p" "$PROPS" | head -1 | tr -d '\r\n'
}

STORE=$(prop storeFile)
case "$STORE" in /*) ;; *) STORE="$ROOT/$STORE" ;; esac
[ -f "$STORE" ] || { echo "keystore.properties names a keystore that is not there: $STORE" >&2; exit 1; }

base64 < "$STORE" | tr -d '\n' | gh secret set OPENWEIGHTS_KEYSTORE_BASE64 -R "$REPO"
prop storePassword | gh secret set OPENWEIGHTS_KEYSTORE_PASSWORD -R "$REPO"
prop keyAlias | gh secret set OPENWEIGHTS_KEY_ALIAS -R "$REPO"
prop keyPassword | gh secret set OPENWEIGHTS_KEY_PASSWORD -R "$REPO"

if [ $# -ge 1 ]; then
  gh secret set PLAY_SERVICE_ACCOUNT_JSON -R "$REPO" < "$1"
fi
if [ -n "${GEMINI_API_KEY:-}" ]; then
  printf %s "$GEMINI_API_KEY" | gh secret set GEMINI_API_KEY -R "$REPO"
fi

gh secret list -R "$REPO"
