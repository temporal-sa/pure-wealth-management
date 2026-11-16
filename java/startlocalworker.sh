#!/bin/bash
# Start the Java open-account worker against a local Temporal dev server (127.0.0.1:7233).
# Usage: startlocalworker.sh <open-account-task-queue>
# Pass the same queue name to src/temporal_supervisor/startlocalworker.sh, so the chat starts
# the open-account workflow there instead of on the Python worker.
if [ -z "$1" ]; then
  echo "**** Missing required parameter: the task queue name for the open-account workflow."
  echo "Use the same name you pass to src/temporal_supervisor/startlocalworker.sh, for example:"
  echo "  ./startlocalworker.sh Open-Account-Java"
  exit 1
fi
cd "$(dirname "$0")" || exit 1
[ -f ../setclaimcheck.sh ] && source ../setclaimcheck.sh
export TEMPORAL_TASK_QUEUE_OPEN_ACCOUNT="$1"
echo "*** Open-account task queue: $TEMPORAL_TASK_QUEUE_OPEN_ACCOUNT"
./gradlew runWorker
