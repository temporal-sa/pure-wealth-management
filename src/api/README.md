# API

A FastAPI app, served at http://127.0.0.1:8000, that the shared React web UI calls. It talks only
to Temporal and Redis.

| Method and path | Parameters | Does |
|-----------------|------------|------|
| `POST /start-workflow` | `workflow_id` | Starts the chat workflow (`WealthManagementWorkflow`), with ID reuse allowed |
| `POST /send-prompt` | `workflow_id`, `prompt` | Sends the `process_user_message` signal |
| `GET /get-chat-history` | `workflow_id`, `from_index` | Returns the events in the Redis list `events:<workflow ID>` from that index. Each event is `{type, content}` |
| `POST /end-chat` | `workflow_id` | Sends the `end_workflow` signal |
| `GET /chaos` | none | The chaos flags, with label, description and on/off state, and the last 20 injected failures |
| `POST /chaos` | `flag`, `enabled` | Turns one flag on or off |
| `POST /chaos/reset` | none | Turns every flag off and clears the failure log |

Start it from the repository root, against a local Temporal dev server and Redis:

```bash
./src/api/startlocalapi.sh
```
