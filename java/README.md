# Java open-account worker

An optional Java (Spring Boot) implementation of `OpenInvestmentAccountWorkflow`, the child workflow
the Open Account Agent starts to open a new investment account. It is a drop-in replacement for the
Python version in
[src/temporal_supervisor/workflows/open_account_workflow.py](../src/temporal_supervisor/workflows/open_account_workflow.py),
with the same KYC and compliance steps, the same saga, and the same status updates to the chat:

* **The saga:** create the account record, debit the linked bank account, then register the account
  with the custodian. If a step fails, the compensations run in reverse order.
* **Status updates:** each change is sent to the parent chat workflow with the
  `update_account_opening_state` signal, which carries `account_name`, `state` and `workflow_id`.

Its workflow ID, input, signals (`verify_kyc`, `compliance_approved`), updates
(`get_client_details`, `update_client_details`) and query (`get_current_state`) match the Python
version, so the chat, the API and the compliance approval scripts work unchanged.

The workflow has no business logic of its own:

* **Business activities are Python:** it calls `get_client`, `update_client`, `open_investment`,
  `remove_investment`, `debit_linked_account`, `credit_linked_account` and `register_account` by
  name on the Python worker's task queue (`TEMPORAL_TASK_QUEUE`, default `Supervisor`). The Python
  worker must therefore be running too, and the chaos panel's flags apply unchanged.
* **The only Java activity:** it sends the status signal to the parent chat workflow, and it runs on
  the open-account task queue with the workflow.

## Claim check

With `setclaimcheck.sh` in the repository root (see
[Claim check and codec server](../src/temporal_supervisor/README.md#claim-check-and-codec-server-optional)),
both scripts turn on the claim check codec in
[claimcheck/](src/main/java/io/temporal/demo/wealthmanagement/claimcheck). It uses the Redis at
`REDIS_HOST` / `REDIS_PORT` (default `localhost:6379`), in the same format as the Python codec. Both the
worker's client and the client that sends status updates to the chat use it.

## Prerequisites

* [Java JDK 25](https://adoptium.net/). The Gradle wrapper (`./gradlew`, Gradle 9.8.2) is
  included, so you don't need to install Gradle.
* Everything the Python demo needs. See the [root README](../README.md#prerequisites).

The first run downloads Gradle and the dependencies, which takes a minute or two.

## Running locally

Start Temporal (`temporal server start-dev`) and Redis (`redis-server`).

The open-account task queue name is the first argument to both workers. Start the Python worker
with it, so the chat starts the open-account workflow on that queue and the Python worker doesn't
register its own version:

```bash
./src/temporal_supervisor/startlocalworker.sh Open-Account-Java
```

Then start the Java worker with the same name:

```bash
./java/startlocalworker.sh Open-Account-Java
```

It connects to `127.0.0.1:7233`, namespace `default` (or `TEMPORAL_ADDRESS` and
`TEMPORAL_NAMESPACE` if they are set). Once it's running, the log shows `Open-account worker
started`. Start the API and the web UI as described in the
[Temporal README](../src/temporal_supervisor/README.md#running-locally), and approve account openings
with `localsendcomplianceapproval.sh` as usual.

To try the Java workflow without the chat or a model key, run the open-account scenarios against it,
with the Java worker running:

```bash
./src/temporal_supervisor/startlocalopenaccount.sh Open-Account-Java
```

## Running on Temporal Cloud

Create `setcloudenv.sh` in the repository root (copy `setcloudenv.example`), as described in
[Running on Temporal Cloud](../src/temporal_supervisor/README.md#running-on-temporal-cloud). Start the
Python worker with the queue name, then the Java worker with the same name:

```bash
./src/temporal_supervisor/startcloudworker.sh Open-Account-Java
```

```bash
./java/startcloudworker.sh Open-Account-Java
```

The script reads `setcloudenv.sh` and works with either authentication method:

* **API key:** with `TEMPORAL_API_KEY` set, it uses the Spring profile `tc-apikey`
  ([application-tc-apikey.yaml](src/main/resources/application-tc-apikey.yaml)).
* **mTLS:** otherwise, it uses the Spring profile `tc`
  ([application-tc.yaml](src/main/resources/application-tc.yaml)), with the certificate and key from
  `TEMPORAL_TLS_CLIENT_CERT_PATH` and `TEMPORAL_TLS_CLIENT_KEY_PATH`.

Java's TLS needs the client key as an unencrypted PKCS8 key (`-----BEGIN PRIVATE KEY-----`). If the
key is in another format, the script stops and prints the command to convert it, for example:

```bash
openssl pkcs8 -topk8 -nocrypt -in /path/to/client.key -out /path/to/client.pkcs8.key
```

Then add the converted key to `setcloudenv.sh`. The Python components keep using
`TEMPORAL_TLS_CLIENT_KEY_PATH`:

```bash
export TEMPORAL_KEY_PKCS8_PATH="/path/to/client.pkcs8.key"
```

With mTLS, the worker has been checked to connect to a Temporal Cloud namespace and poll its task
queue. The API key path and a full account opening on Temporal Cloud haven't been tested.

## Bug in workflow code (chaos panel)

The chaos panel's **Bug in workflow code** switch gives new account openings the promo code
`WELCOME-BONUS`. The Java version has its own bug, in
[OpenInvestmentAccountWorkflowImpl.java](src/main/java/io/temporal/demo/wealthmanagement/workflow/OpenInvestmentAccountWorkflowImpl.java):

* **The bug:** `BONUS_RATES` is keyed by `"WELCOME_BONUS"` (underscore), so `welcomeBonus` throws a
  `NullPointerException` right after compliance approval.
* **What happens:** the exception fails the workflow task, not the workflow. The workflow stays
  Running, paused at that line.
* **The fix:** use the commented-out `BONUS_RATES` line above it, then restart the Java worker. The
  workflow resumes on the next workflow-task retry.

To re-arm the bug afterwards, run this from the repository root:

```bash
git checkout java/src/main/java/io/temporal/demo/wealthmanagement/workflow/OpenInvestmentAccountWorkflowImpl.java
```

See [Failure scenarios](../src/temporal_supervisor/README.md#failure-scenarios) for the other scenarios.

## Tests

The tests cover the saga, rollback in reverse, transient-failure retries, the transfer IDs, the
workflow bug, and the claim check codec (including decoding what the Python codec writes). They use the Temporal Java SDK's in-memory test environment with stub activities, so
they need no Temporal server, Redis or model key:

```bash
cd java && ./gradlew test
```

## Differences from the reference port's Java worker

The unused service and starter classes are left out. The Temporal Cloud script also supports an API
key, not only mTLS. When claim check is on, the status client uses the same codec as the worker, and a
missing claim check fails with a clear error.
