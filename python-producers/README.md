# Python Producers

Two Python producers fetch external API data and publish Kafka events that match the Spring consumer's `EventDTO` contract and payload models.

A separate Python auth API handles the interactive login/authentication flows. The pollers only read saved state and poll external APIs.

## Selected platforms

### Dev: GitHub REST API

* Configured here specifically for public commit tracking only.
* Works without a token by polling GitHub's public events API, though a token is still optional for higher rate limits.

### Transactions: Plaid Transactions Sync API

* Good fit for transaction ingestion because it returns normalized transactions with stable transaction ids.
* Low-maintenance fit because `transactions/sync` is incremental via cursor, so the poller can run repeatedly without duplicating old transactions.

## Install

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r python-producers/requirements.txt
cp python-producers/.env.example .env
```

## Topics

Create these topics if they do not already exist:

* `saket.dev_activity`
* `saket.wallet`
## Run

```bash
source .venv/bin/activate
set -a
source .env
set +a
python python-producers/github_dev_producer.py
python python-producers/plaid_transaction_producer.py
```

## Auth API

Run the auth API locally:

```bash
source .venv/bin/activate
set -a
source .env
set +a
python python-producers/auth_api.py
```

Default local address:

```text
http://localhost:8000
```

Available endpoints:

* `GET /health`
* `GET /auth/test`
* `POST /pollers/github/run`
* `POST /pollers/plaid/run`
* `POST /pollers/run-all`
* `POST /auth/plaid/link-token`
* `POST /auth/plaid/exchange-public-token`
* `POST /auth/plaid/sandbox-seed`

For manual testing before the real frontend exists, open:

```text
http://localhost:8000/auth/test
```

That page contains the tester auth buttons:

* `Connect Plaid`
* `Seed Plaid Sandbox Item`
* `Run All Pollers`

## Docker

Build from the repository root so the Dockerfile can copy the producer sources:

```bash
docker build -f python-producers/Dockerfile -t productivity-python-producers .
docker run --rm --env-file .env productivity-python-producers
```

For true set-and-forget behavior in Docker, persist the poller state files so cursors survive container restarts:

```bash
docker run --rm --env-file .env \
  -v "$(pwd)/python-producers/.state:/app/python-producers/.state" \
  productivity-python-producers
```

When the producers run inside Docker on the Compose network, use `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`.
Use `localhost:9092` only when the Python process runs directly on the host machine.

The container supports these modes:

* `RUN_MODE=loop` for interval polling
* `RUN_MODE=once` for a single execution
* `RUN_MODE=api` for the auth API

Loop mode runs one producer every 15 minutes by default. Select the producer with `PRODUCER_SCRIPT`:

```bash
docker run --rm --env-file .env \
  -e PRODUCER_SCRIPT=github_dev_producer.py \
  productivity-python-producers

docker run --rm --env-file .env \
  -e PRODUCER_SCRIPT=plaid_transaction_producer.py \
  productivity-python-producers
```

Adjust the cadence with `POLL_INTERVAL_SECONDS`. `900` means 15 minutes.

To run the auth API in Docker:

```bash
docker run --rm --env-file .env -p 8000:8000 \
  -v "$(pwd)/python-producers/.state:/app/python-producers/.state" \
  -e RUN_MODE=api \
  productivity-python-producers
```

## One-Time Auth

The intended workflow is:

* GitHub: set `GITHUB_USERNAME` and optionally `GITHUB_TOKEN`
* Plaid: complete Plaid Link once, then let the poller read the saved access token and cursor

### Plaid Local Flow

1. Start the auth API.
2. Your frontend calls `POST /auth/plaid/link-token`.
3. Initialize Plaid Link in the browser using the returned `link_token`.
4. The user signs into their bank through Plaid Link.
5. Plaid Link returns a `public_token`.
6. Your frontend posts that `public_token` to `POST /auth/plaid/exchange-public-token`.
7. The auth API exchanges it for a long-lived `access_token` and saves it to `python-producers/.state/plaid.json`.
8. Future poller runs use the saved access token and cursor.

### Plaid Sandbox Shortcut

If you just want a fast sandbox Item without running Plaid Link, open `http://localhost:8000/auth/test` and click `Seed Plaid Sandbox Item`.

That endpoint:

* calls Plaid `/sandbox/public_token/create`
* exchanges the resulting `public_token`
* stores the saved `access_token` in `python-producers/.state/plaid.json`

Default sandbox settings:

* `PLAID_SANDBOX_INSTITUTION_ID=ins_109508`
* `PLAID_SANDBOX_USERNAME=user_transactions_dynamic`
* `PLAID_SANDBOX_PASSWORD=pass_good`

## Event mapping

### GitHub -> `DevLog`

The producer emits:

* one event per commit from public `PushEvent` payloads
* `timestamp` from GitHub push `created_at`
* `platform=github`
* `actionType=commit`
* `target` from `repo.name`
* `metadata` with commit SHA, commit message, author info, ref, and repo details

### Plaid -> `TransactionLog`

The producer emits:

* `externTxnId` from `transaction_id`
* `timestamp` from `authorized_datetime`, `datetime`, or a noon UTC fallback for `date`
* `amount` from Plaid `amount`
* `category` from personal finance category data when present
