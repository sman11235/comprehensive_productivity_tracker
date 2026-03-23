from __future__ import annotations

from collections.abc import Callable

from flask import Flask, jsonify, request, Response

from producer_common import JsonApiClient, env, load_json_file, save_json_file
from github_dev_producer import sync_github_events
from plaid_transaction_producer import sync_transactions

app = Flask(__name__)


PollerFn = Callable[[], int]


def plaid_state_file() -> str:
    return env("PLAID_STATE_FILE", default="python-producers/.state/plaid.json")


def plaid_client() -> JsonApiClient:
    return JsonApiClient(base_url=env("PLAID_BASE_URL", default="https://sandbox.plaid.com"))


def plaid_link_redirect_uri() -> str:
    return env("PLAID_REDIRECT_URI", default="")


def update_plaid_state_credentials(*, access_token: str, item_id: str | None) -> None:
    state = load_json_file(plaid_state_file(), default={})
    previous_item_id = state.get("item_id")
    if state.get("cursor") and item_id and previous_item_id != item_id:
        # Plaid cursors are scoped to an item; reseeding or relinking must drop the old cursor.
        state.pop("cursor", None)

    state["access_token"] = access_token
    state["item_id"] = item_id
    save_json_file(plaid_state_file(), state)


def run_github_poller() -> int:
    return sync_github_events(
        username=env("GITHUB_USERNAME", default=""),
        bootstrap_servers=env("KAFKA_BOOTSTRAP_SERVERS", default="localhost:9092"),
        device_id=env("DEV_DEVICE_ID", default="github-producer"),
        state_file=env("GITHUB_STATE_FILE", default="python-producers/.state/github.json"),
        limit=int(env("GITHUB_EVENT_LIMIT", default="30")),
    )


def run_plaid_poller() -> int:
    return sync_transactions(
        bootstrap_servers=env("KAFKA_BOOTSTRAP_SERVERS", default="localhost:9092"),
        device_id=env("TXN_DEVICE_ID", default="plaid-producer"),
        state_file=env("PLAID_STATE_FILE", default="python-producers/.state/plaid.json"),
        count=int(env("PLAID_COUNT", default="100")),
    )


def available_pollers() -> dict[str, PollerFn]:
    return {
        "github": run_github_poller,
        "plaid": run_plaid_poller,
    }


@app.get("/health")
def health() -> tuple[dict[str, object], int]:
    plaid_state = load_json_file(plaid_state_file(), default={})
    return (
        {
            "ok": True,
            "plaid_connected": bool(plaid_state.get("access_token")),
        },
        200,
    )


@app.get("/auth/test")
def auth_test_page() -> Response:
    html = """<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Auth Test</title>
    <script src="https://cdn.plaid.com/link/v2/stable/link-initialize.js"></script>
    <style>
      body {
        font-family: sans-serif;
        max-width: 640px;
        margin: 48px auto;
        padding: 0 16px;
      }
      button {
        display: block;
        width: 100%;
        margin: 12px 0;
        padding: 14px 16px;
        font-size: 16px;
      }
      pre {
        background: #f5f5f5;
        padding: 12px;
        white-space: pre-wrap;
      }
    </style>
  </head>
  <body>
    <h1>Auth Test</h1>
    <button id="plaid-button" type="button">Connect Plaid</button>
    <button id="plaid-sandbox-button" type="button">Seed Plaid Sandbox Item</button>
    <button id="pollers-button" type="button">Run All Pollers</button>
    <pre id="status">Ready.</pre>
    <script>
      const statusEl = document.getElementById("status");

      function setStatus(value) {
        statusEl.textContent = value;
      }

      document.getElementById("plaid-button").addEventListener("click", async () => {
        try {
          setStatus("Requesting Plaid link token...");
          const linkTokenResp = await fetch("/auth/plaid/link-token", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({})
          });
          const linkTokenData = await linkTokenResp.json();
          if (!linkTokenResp.ok) {
            throw new Error(JSON.stringify(linkTokenData));
          }

          setStatus("Opening Plaid Link...");
          const handler = Plaid.create({
            token: linkTokenData.link_token,
            onSuccess: async (public_token, metadata) => {
              setStatus("Exchanging Plaid public token...");
              const exchangeResp = await fetch("/auth/plaid/exchange-public-token", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ public_token })
              });
              const exchangeData = await exchangeResp.json();
              if (!exchangeResp.ok) {
                throw new Error(JSON.stringify(exchangeData));
              }
              setStatus("Plaid connected. " + JSON.stringify({
                item_id: exchangeData.item_id,
                institution: metadata.institution ? metadata.institution.name : null
              }, null, 2));
            },
            onExit: (err, metadata) => {
              if (err) {
                setStatus("Plaid exited with error: " + JSON.stringify(err, null, 2));
                return;
              }
              setStatus("Plaid exited without connecting.");
            }
          });
          handler.open();
        } catch (error) {
          setStatus("Plaid setup failed: " + error.message);
        }
      });

      document.getElementById("plaid-sandbox-button").addEventListener("click", async () => {
        try {
          setStatus("Creating Plaid sandbox item...");
          const resp = await fetch("/auth/plaid/sandbox-seed", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({})
          });
          const data = await resp.json();
          if (!resp.ok) {
            throw new Error(JSON.stringify(data));
          }
          setStatus("Plaid sandbox item created. " + JSON.stringify(data, null, 2));
        } catch (error) {
          setStatus("Plaid sandbox seed failed: " + error.message);
        }
      });

      document.getElementById("pollers-button").addEventListener("click", async () => {
        try {
          setStatus("Running all pollers...");
          const resp = await fetch("/pollers/run-all", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({})
          });
          const data = await resp.json();
          if (!resp.ok) {
            throw new Error(JSON.stringify(data));
          }
          setStatus("Pollers finished. " + JSON.stringify(data, null, 2));
        } catch (error) {
          setStatus("Pollers failed: " + error.message);
        }
      });
    </script>
  </body>
</html>
"""
    return Response(html, mimetype="text/html")


@app.post("/pollers/<name>/run")
def run_named_poller(name: str):
    pollers = available_pollers()
    poller = pollers.get(name)
    if poller is None:
        return jsonify({"ok": False, "error": "unknown_poller", "available": sorted(pollers)}), 404

    try:
        published_count = poller()
    except Exception as exc:
        return jsonify({"ok": False, "poller": name, "error": str(exc)}), 500

    return jsonify({"ok": True, "poller": name, "published_count": published_count}), 200


@app.post("/pollers/run-all")
def run_all_pollers():
    results: list[dict[str, object]] = []
    has_error = False

    for name, poller in available_pollers().items():
        try:
            published_count = poller()
            results.append({"poller": name, "ok": True, "published_count": published_count})
        except Exception as exc:
            has_error = True
            results.append({"poller": name, "ok": False, "error": str(exc)})

    status_code = 207 if has_error else 200
    return jsonify({"ok": not has_error, "results": results}), status_code


@app.post("/auth/plaid/link-token")
def create_plaid_link_token():
    body = request.get_json(silent=True) or {}
    user_id = body.get("user_id") or env("PLAID_DEFAULT_USER_ID", default="local-user")
    products = [item.strip() for item in env("PLAID_PRODUCTS", default="transactions").split(",") if item.strip()]
    country_codes = [item.strip() for item in env("PLAID_COUNTRY_CODES", default="US").split(",") if item.strip()]

    payload: dict[str, object] = {
        "client_id": env("PLAID_CLIENT_ID", required=True),
        "secret": env("PLAID_SECRET", required=True),
        "client_name": env("PLAID_CLIENT_NAME", default="Productivity Tracker"),
        "country_codes": country_codes,
        "language": env("PLAID_LANGUAGE", default="en"),
        "products": products,
        "user": {"client_user_id": user_id},
    }

    redirect_uri = plaid_link_redirect_uri()
    if redirect_uri:
        payload["redirect_uri"] = redirect_uri

    response = plaid_client().post("/link/token/create", payload=payload)
    return jsonify(response), 200


@app.post("/auth/plaid/exchange-public-token")
def exchange_plaid_public_token():
    body = request.get_json(silent=True) or {}
    public_token = body.get("public_token", "")
    if not public_token:
        return jsonify({"ok": False, "error": "missing_public_token"}), 400

    response = plaid_client().post(
        "/item/public_token/exchange",
        payload={
            "client_id": env("PLAID_CLIENT_ID", required=True),
            "secret": env("PLAID_SECRET", required=True),
            "public_token": public_token,
        },
    )
    update_plaid_state_credentials(access_token=response["access_token"], item_id=response.get("item_id"))
    return jsonify({"ok": True, "item_id": response.get("item_id")}), 200


@app.post("/auth/plaid/sandbox-seed")
def seed_plaid_sandbox_item():
    body = request.get_json(silent=True) or {}
    institution_id = body.get("institution_id") or env("PLAID_SANDBOX_INSTITUTION_ID", default="ins_109508")
    initial_products = [item.strip() for item in env("PLAID_PRODUCTS", default="transactions").split(",") if item.strip()]

    public_token_response = plaid_client().post(
        "/sandbox/public_token/create",
        payload={
            "client_id": env("PLAID_CLIENT_ID", required=True),
            "secret": env("PLAID_SECRET", required=True),
            "institution_id": institution_id,
            "initial_products": initial_products,
            "options": {
                "override_username": env("PLAID_SANDBOX_USERNAME", default="user_transactions_dynamic"),
                "override_password": env("PLAID_SANDBOX_PASSWORD", default="pass_good"),
            },
        },
    )

    exchange_response = plaid_client().post(
        "/item/public_token/exchange",
        payload={
            "client_id": env("PLAID_CLIENT_ID", required=True),
            "secret": env("PLAID_SECRET", required=True),
            "public_token": public_token_response["public_token"],
        },
    )

    update_plaid_state_credentials(
        access_token=exchange_response["access_token"],
        item_id=exchange_response.get("item_id"),
    )
    state = load_json_file(plaid_state_file(), default={})
    state["sandbox_institution_id"] = institution_id
    save_json_file(plaid_state_file(), state)

    return (
        jsonify(
            {
                "ok": True,
                "item_id": exchange_response.get("item_id"),
                "institution_id": institution_id,
                "username": env("PLAID_SANDBOX_USERNAME", default="user_transactions_dynamic"),
            }
        ),
        200,
    )


@app.get("/auth/plaid/config")
def get_plaid_config():
    query = urlencode({"user_id": env("PLAID_DEFAULT_USER_ID", default="local-user")})
    return (
        jsonify(
            {
                "link_token_endpoint": "/auth/plaid/link-token",
                "public_token_exchange_endpoint": "/auth/plaid/exchange-public-token",
                "sandbox_seed_endpoint": "/auth/plaid/sandbox-seed",
                "example_link_token_request": f"/auth/plaid/link-token?{query}",
            }
        ),
        200,
    )


def main() -> None:
    host = env("AUTH_API_HOST", default="0.0.0.0")
    port = int(env("AUTH_API_PORT", default="8000"))
    app.run(host=host, port=port, debug=False)


if __name__ == "__main__":
    main()
