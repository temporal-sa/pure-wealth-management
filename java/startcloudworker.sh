#!/bin/bash
# Start the Java open-account worker against Temporal Cloud, using the settings in
# ../setcloudenv.sh (copy ../setcloudenv.example), with either an API key or mTLS.
# Usage: startcloudworker.sh <open-account-task-queue>
# Pass the same queue name to src/temporal_supervisor/startcloudworker.sh.
if [ -z "$1" ]; then
  echo "**** Missing required parameter: the task queue name for the open-account workflow."
  echo "Use the same name you pass to src/temporal_supervisor/startcloudworker.sh, for example:"
  echo "  ./startcloudworker.sh Open-Account-Java"
  exit 1
fi
cd "$(dirname "$0")" || exit 1
[ -f ../setclaimcheck.sh ] && source ../setclaimcheck.sh
export TEMPORAL_TASK_QUEUE_OPEN_ACCOUNT="$1"

if [ ! -f ../setcloudenv.sh ]; then
  echo "**** setcloudenv.sh not found. From the repository root, run:"
  echo "  cp setcloudenv.example setcloudenv.sh"
  echo "then edit it for your Temporal Cloud namespace."
  exit 1
fi
source ../setcloudenv.sh

for var in TEMPORAL_ADDRESS TEMPORAL_NAMESPACE; do
  if [ -z "${!var}" ]; then
    echo "**** $var is not set. Check setcloudenv.sh."
    exit 1
  fi
done

if [ -n "$TEMPORAL_API_KEY" ]; then
  # API key: application-tc-apikey.yaml
  export SPRING_PROFILES_ACTIVE=tc-apikey
  AUTH="API key"
else
  # mTLS: application-tc.yaml, which reads TEMPORAL_CERT_PATH and TEMPORAL_KEY_PKCS8_PATH
  export TEMPORAL_CERT_PATH="${TEMPORAL_CERT_PATH:-$TEMPORAL_TLS_CLIENT_CERT_PATH}"
  KEY_PATH="${TEMPORAL_KEY_PKCS8_PATH:-$TEMPORAL_TLS_CLIENT_KEY_PATH}"
  if [ -z "$TEMPORAL_CERT_PATH" ] || [ -z "$KEY_PATH" ]; then
    echo "**** Set either TEMPORAL_API_KEY, or TEMPORAL_TLS_CLIENT_CERT_PATH and"
    echo "TEMPORAL_TLS_CLIENT_KEY_PATH, in setcloudenv.sh."
    exit 1
  fi
  for file in "$TEMPORAL_CERT_PATH" "$KEY_PATH"; do
    if [ ! -r "$file" ]; then
      echo "**** Cannot read $file. Check the paths in setcloudenv.sh."
      exit 1
    fi
  done
  # Java's TLS needs the key in unencrypted PKCS8 format (-----BEGIN PRIVATE KEY-----). Keys
  # from tcld or openssl are often PKCS1 (-----BEGIN RSA PRIVATE KEY-----) or SEC1 (EC).
  if ! grep -q -- "-----BEGIN PRIVATE KEY-----" "$KEY_PATH"; then
    PKCS8_PATH="${KEY_PATH%.*}.pkcs8.key"
    echo "**** The client key $KEY_PATH is not an unencrypted PKCS8 key, which the Java worker needs."
    echo "Convert it with:"
    echo ""
    echo "  openssl pkcs8 -topk8 -nocrypt -in \"$KEY_PATH\" -out \"$PKCS8_PATH\""
    echo ""
    echo "then add this line to setcloudenv.sh (the Python components keep using TEMPORAL_TLS_CLIENT_KEY_PATH):"
    echo ""
    echo "  export TEMPORAL_KEY_PKCS8_PATH=\"$PKCS8_PATH\""
    exit 1
  fi
  export TEMPORAL_KEY_PKCS8_PATH="$KEY_PATH"
  export SPRING_PROFILES_ACTIVE=tc
  AUTH="mTLS"
fi

echo "*** Connecting the open-account worker to $TEMPORAL_NAMESPACE at $TEMPORAL_ADDRESS ($AUTH)"
echo "*** Open-account task queue: $TEMPORAL_TASK_QUEUE_OPEN_ACCOUNT"
./gradlew runWorker
