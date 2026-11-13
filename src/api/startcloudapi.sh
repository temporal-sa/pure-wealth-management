#!/bin/bash
# Start the API for the web UI on http://127.0.0.1:8000, against Temporal Cloud
# (settings in setcloudenv.sh in the repository root).
cd "$(dirname "$0")/.." || exit 1
source ./temporal_supervisor/_cloudenv.sh
# The key isn't needed here, but LLM_MODEL names the model in the chaos panel.
[ -f ../setllmkey.sh ] && source ../setllmkey.sh
uv run uvicorn api.main:app --reload
