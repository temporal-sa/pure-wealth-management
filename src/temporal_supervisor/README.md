# Temporal version

The four agents run inside a Temporal workflow. The agent loop
([common/agent_loop.py](../common/agent_loop.py)) is ordinary workflow code: every model call is
the `invoke_model` activity, and every tool that touches a system is an activity. A worker crash,
a model outage or a tool outage pauses the conversation instead of losing it. When things recover,
Temporal resumes the turn where it stopped.

## Architecture

```
React UI ──► API (FastAPI) ──► Temporal ──► Worker
   ▲              │                          ├── WealthManagementWorkflow   (one per chat; runs the agent loop)
   └── polls ─────┤                          │     ├── invoke_model activity        (task queue <queue>-llm)
                  ▼                          │     ├── tool activities              (beneficiaries, investments, ...)
                Redis ◄──── event stream ────┤     └── OpenInvestmentAccountWorkflow (child, one per account opening)
           (chat + chaos flags)              │           KYC ► compliance approval ► saga
                                             └── compliance officer: localsendcomplianceapproval.sh
```

* **The chat workflow** `WealthManagementWorkflow` runs for the whole conversation. Customer
  messages arrive as the `process_user_message` signal and are processed one at a time. After
  each turn, the reply is appended to the Redis list `events:<workflow ID>`, which the API serves
  to the UI. The conversation (messages, active agent, client ID, account openings) is workflow
  state, so it survives replay.
* **Model calls** run as the `invoke_model` activity on their own task queue,
  `<TEMPORAL_TASK_QUEUE>-llm`. LiteLLM's own retries are off, so Temporal's retry policy decides
  when a failed call is retried. `LLM_MAX_CALLS_PER_SECOND` has the Temporal service limit how
  often model calls are dispatched across all workers.
* **Tools** run as activities with unlimited retries and start-to-close timeouts, so an outage is
  waited out rather than timed out. Business failures (deposit limits, insufficient funds,
  custodian rejection) are non-retryable `ApplicationError`s.
* **Account opening** runs as a child workflow, `OpenInvestmentAccountWorkflow`. It reports
  each state change to the chat workflow with the `update_account_opening_state` signal, and the
  chat shows it in the UI.

## Running locally

From the repository root, each in its own terminal (see the
[root README](../../README.md#prerequisites) for prerequisites and the model key):

```bash
temporal server start-dev
```

```bash
redis-server
```

```bash
./src/temporal_supervisor/startlocalworker.sh
```

```bash
./src/api/startlocalapi.sh
```

```bash
cd src/frontend && npm install && npm start
```

Open http://localhost:3000 for the chat, and http://localhost:8233 for the Temporal UI.

`startlocalworker.sh` takes an optional task queue name. With one, the open-account workflow runs
on that queue and this worker doesn't register it. That's for a separate open-account worker,
such as the Java one in the other ports (not included here).

## Running on Temporal Cloud

This port doesn't include the cloud start scripts (an optional component). The worker, the API and
the scripts connect using Temporal's
[environment configuration](https://docs.temporal.io/develop/environment-configuration), so they
read `TEMPORAL_ADDRESS`, `TEMPORAL_NAMESPACE`, `TEMPORAL_API_KEY` or the mTLS certificate and key
paths from the environment or a config profile. This hasn't been tested against Temporal Cloud.

## How account opening works

1. The customer asks to open an account, for example "Open Vacation with $13,000". The Open
   Account Agent calls `open_new_investment_account`.
2. The tool checks the deposit rules first (`validate_new_account`: $100 to $1,000,000). A refusal
   goes back to the agent, which explains it, and nothing is started.
3. The tool starts the child workflow with the ID
   `OpenAccount-<chat workflow ID>-<client ID>-<account name>-<n>`, where `<n>` counts the openings
   started in this chat (a refused request doesn't use up a number). **Every request starts a new
   opening**, even for an account name that was opened before in the same chat.
4. The agent shows the customer their details (`get_current_client_info`, which runs the child's
   `get_client_details` update). It approves KYC (`approve_kyc`, which sends `verify_kyc`) only after
   the customer confirms in a later message. If the customer changes a detail instead,
   `update_client_details` saves it and completes KYC.
5. The opening waits for compliance approval, which comes from outside the chat:

   ```bash
   ./src/temporal_supervisor/localsendcomplianceapproval.sh <open-account workflow ID>
   ```

6. The saga then creates the account record, transfers the deposit from the linked bank account,
   and registers the account with the custodian. If a step fails with a non-retryable error, the
   completed steps are undone in reverse order and the state ends with
   `Rolled back. The account was not opened: <reason>.`

The agent reads the opening's state with `get_account_opening_status`, never from memory. Every
agent is also told, on every model call, each opening started in the chat and its current state,
so an agent that didn't start an opening (such as the Investment Agent listing accounts) reports
it correctly instead of guessing.

To find an opening's workflow ID, list the openings in the Temporal UI, or run:

```bash
temporal workflow list --query "WorkflowId STARTS_WITH 'OpenAccount-'"
```

### Running the open-account workflow on its own

`startlocalopenaccount.sh` starts a worker (no model key needed) and runs the open-account workflow
directly, with no chat, for three scenarios: KYC approved, client details updated, and compliance
approval sent by hand. For the third, it prints the workflow ID to approve. Run
`localsendcomplianceapproval.sh` with that ID in another terminal. It stops its worker on exit.

```bash
./src/temporal_supervisor/startlocalopenaccount.sh
```

```bash
./src/temporal_supervisor/localsendcomplianceapproval.sh testing-open-investment-account-workflow3
```

These scenarios change the demo data. Reset it with `git checkout data/`.

## Environment variables

| Variable | Default | Used by | Meaning |
|----------|---------|---------|---------|
| `LLM_MODEL` | `anthropic/claude-opus-5-5` | Worker, standalone, API (chaos labels) | LiteLLM model name, `<provider>/<model>` |
| `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` / `GEMINI_API_KEY` | none | Worker, standalone | The key for the provider in `LLM_MODEL` |
| `TEMPORAL_ADDRESS` | `127.0.0.1:7233` | Worker, API, scripts | Temporal service address |
| `TEMPORAL_NAMESPACE` | `default` | Worker, API, scripts | Temporal namespace |
| `TEMPORAL_TASK_QUEUE` | `Supervisor` | Worker, API | Task queue of the chat workflow and its tools |
| `TEMPORAL_TASK_QUEUE_OPEN_ACCOUNT` | the chat queue | Worker, runner | Task queue of the open-account workflow (set by `startlocalworker.sh <queue>`) |
| `TEMPORAL_TASK_QUEUE_LLM` | `<TEMPORAL_TASK_QUEUE>-llm` | Worker | Task queue of the model calls |
| `LLM_MAX_CALLS_PER_SECOND` | unlimited | Worker | Dispatch rate limit for model calls, across all workers |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Worker, API | Redis for the event stream and the chaos flags |

## Failure scenarios

Switch failures on and off from the chaos panel in the web UI while the demo runs, with no restart.
Activities and the model call read the flags from the Redis hash `chaos:flags` on every attempt. If
Redis can't be reached, the flags read as off. Each injected failure is logged to the Redis list
`chaos:events` for the panel.

| Flag | Panel label | What happens |
|------|-------------|--------------|
| `tool_down` | Core banking API down | Beneficiary and investment activities raise a connection error. They retry until the flag is off, and the agent's reply waits mid-turn: nothing is lost or asked again. |
| `llm_down` | LLM API down | Every model call raises a connection error. The model activity retries, and the turn resumes where it stopped. |
| `llm_rate_limited` | LLM rate limited (429) | Every model call fails with a 429 that sets a next retry delay of 15 s, as a `Retry-After` header would, overriding the backoff. A real 429 from the provider is handled the same way, using its own `Retry-After`. |
| `custodian_rejects` | Custodian rejects new accounts | `register_account` raises a non-retryable `CustodianRejected` error. The saga refunds the deposit, then removes the account record. |
| `slow_saga` | Slow account-opening steps | Each saga step sleeps 4 s, so you can watch the saga, or kill the worker partway through it and restart it. |
| `workflow_bug` | Bug in workflow code | `find_promotion` returns the promo code `WELCOME-BONUS`, whose code path has a bug (see below). |

Scenarios that need no flag:

* **Business rule:** a deposit under $100 or over $1,000,000 fails `validate_new_account` on the
  first attempt, and the agent explains why.
* **Insufficient funds:** client 123 opening an account with $300,000 fails the debit, since the
  linked bank account holds $250,000. The saga rolls back.
* **Proactive rate limiting:** start the worker with `LLM_MAX_CALLS_PER_SECOND` set, for example
  `LLM_MAX_CALLS_PER_SECOND=0.2`, and model calls queue up on the `-llm` task queue instead of
  hitting the provider's rate limit.

### Bug in workflow code

With the `workflow_bug` flag on, new openings get the `WELCOME-BONUS` promo code. After
compliance approval, the welcome-bonus calculation in `_welcome_bonus`
([workflows/open_account_workflow.py](workflows/open_account_workflow.py)) reads
`inputs.initial_deposit`, a field that doesn't exist (it's `initial_amount`), and raises an
`AttributeError`. That fails the **workflow task**, not the workflow. The workflow stays Running,
with the compliance approval kept, and Temporal retries the task. The Temporal UI shows the stack
trace.

Turning the flag off doesn't unstick an opening that already has the promo code. To fix it, edit
the line marked `*** Bug-In-Workflow-Code ***`: uncomment the fix directly above it and comment out
the buggy line. Then restart the worker. On the next workflow-task retry, the workflow resumes and
opens the account with the bonus (1% of the deposit, up to $500). To re-arm the demo, put the line
back.

## Tests

The default suite needs no model key, no Redis and no running Temporal service. It uses Temporal's
local test server (downloaded on first run). From the repository root:

```bash
uv run pytest
```

| Test file | Checks |
|-----------|--------|
| `test_failure_scenarios.py` | Saga success; rollback in reverse after a custodian rejection and after a failed debit; transient failures retried, not rolled back; transfer IDs include the run ID; the workflow bug pauses the workflow without failing it; deposit rules; idempotent transfers; tool and model outages; the 429 retry delay, from the chaos panel and from a real `Retry-After` |
| `test_kyc_guard.py` | `approve_kyc` refuses until this opening's details were shown in an earlier turn |
| `test_repeat_opening.py` | Two "Vacation" openings in one chat start `...-Vacation-1` and `...-Vacation-2`, both run, the second needs its own KYC, and the children's states reach the event stream |
| `test_client_id.py` | Every agent is told the client ID once a tool has used it |
| `test_opening_status_notes.py` | Every agent is told each account opening's request and current state |
| `test_off_topic.py` | The Supervisor Agent's prompt, in both versions, declines off-topic questions |
| `test_agents.py` | The agents, tools and handoffs match the PRD in both versions, and the open-account prompt has the PRD's lines |
| `test_agent_loop.py` | The agent loop on its own: same-turn handoffs, tool schemas, tool errors, the client ID |

The chat workflow tests replace the model with a scripted one. It is just an activity registered
under the name `invoke_model` ([tests/scripted_model.py](../../tests/scripted_model.py)).

The live tests call the real model (`LLM_MODEL`), so they are kept out of the default run. They
repeat the conversation `LIVE_TEST_RUNS` times (default 1) and print each transcript:

```bash
source ./setllmkey.sh && LIVE_TEST_RUNS=3 uv run pytest tests/live_*.py -s
```

* `live_agent_handoffs.py`: "Who are my beneficiaries?", "123", "What investments do I have?". In
  the last turn, the Beneficiary Agent hands off to the supervisor, which hands off to the
  Investment Agent, which lists Checking, Savings and 401K without asking for the client ID again.
  No agent mentions a transfer.
* `live_repeat_opening.py`: the same three messages, then "Open Vacation with $13,000", "Yes" and
  "Open Vacation with $13,000" again. Both open replies show the KYC details, and child workflows
  ending `-Vacation-1` and `-Vacation-2` were started.
* `live_opening_status.py`: two "Vacation" openings, the first rolled back and the second
  complete. Listing the investments afterwards must not claim an opening is still pending or that
  a duplicate account may appear.
