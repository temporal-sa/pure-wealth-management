#!/bin/bash
#
# Sourced by the worker scripts. If a task queue name is passed, the open-account
# workflow runs on that queue instead of the chat queue, and this port's worker
# doesn't register it: start the worker that polls that queue as well.
#
if [ -n "$1" ]; then
  echo "*** Using $1 as the task queue for the open-account workflow. ***"
  export TEMPORAL_TASK_QUEUE_OPEN_ACCOUNT="$1"
  echo "*** Remember to also start the worker that polls $1 ***"
fi
