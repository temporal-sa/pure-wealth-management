#!/bin/bash
# Start the API for the web UI on http://127.0.0.1:8000, against a local Temporal dev server.
cd "$(dirname "$0")/.." || exit 1
[ -f ../setclaimcheck.sh ] && source ../setclaimcheck.sh
# The key isn't needed here, but LLM_MODEL names the provider in the chaos panel.
[ -f ../setllmkey.sh ] && source ../setllmkey.sh
uv run uvicorn api.main:app --reload
