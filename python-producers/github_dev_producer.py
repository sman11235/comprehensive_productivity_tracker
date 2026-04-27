from __future__ import annotations

import argparse
from typing import Any

from producer_common import JsonApiClient, KafkaEventPublisher, build_event, env, load_json_file, save_json_file, to_iso8601

TOPIC = "saket.dev_activity"
EVENT_TYPE = "saket.dev_activity"
SOURCE = "api.github"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fetch GitHub user events and publish commit Kafka events.")
    parser.add_argument("--username", default=env("GITHUB_USERNAME", default=""))
    parser.add_argument("--bootstrap-servers", default=env("KAFKA_BOOTSTRAP_SERVERS", default="localhost:9092"))
    parser.add_argument("--device-id", default=env("DEV_DEVICE_ID", default="github-producer"))
    parser.add_argument("--state-file", default=env("GITHUB_STATE_FILE", default="python-producers/.state/github.json"))
    parser.add_argument("--limit", type=int, default=int(env("GITHUB_EVENT_LIMIT", default="30")))
    return parser.parse_args()


def github_client() -> JsonApiClient:
    headers: dict[str, str] = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    token = env("GITHUB_TOKEN", default="")
    if token:
        headers["Authorization"] = f"Bearer {token}"

    return JsonApiClient(base_url="https://api.github.com", headers=headers)


def fetch_events(username: str, limit: int) -> list[dict[str, Any]]:
    client = github_client()
    params = {"per_page": min(limit, 100)}
    token = env("GITHUB_TOKEN", default="")
    path = f"/users/{username}/events" if token else f"/users/{username}/events/public"
    response = client.get(path, params=params)
    return response if isinstance(response, list) else []


def fetch_push_commits(repo: str, before: str | None, head: str | None) -> list[dict[str, Any]]:
    if not repo or not head:
        return []

    if before and before != head:
        try:
            comparison = github_client().get(f"/repos/{repo}/compare/{before}...{head}")
        except Exception:
            comparison = None
        if isinstance(comparison, dict):
            commits = comparison.get("commits")
            if isinstance(commits, list) and commits:
                return commits

    try:
        commit = github_client().get(f"/repos/{repo}/commits/{head}")
    except Exception:
        return []

    return [commit] if isinstance(commit, dict) else []


def commit_details(commit: dict[str, Any]) -> tuple[str | None, str, dict[str, Any]]:
    sha = commit.get("sha")
    if not sha:
        return None, "", {}

    message = commit.get("message")
    if not isinstance(message, str):
        message = ((commit.get("commit") or {}).get("message")) or ""

    author = commit.get("author")
    if not isinstance(author, dict):
        author = ((commit.get("commit") or {}).get("author")) or {}

    return sha, message, author


def commit_events(push_event: dict[str, Any], username: str, device_id: str) -> list[dict[str, Any]]:
    payload = push_event.get("payload") or {}
    repo = (push_event.get("repo") or {}).get("name", "unknown")
    observed_at = to_iso8601(push_event.get("created_at"))
    actor = (push_event.get("actor") or {}).get("login")
    ref = payload.get("ref")
    head = payload.get("head")
    before = payload.get("before")

    commits = payload.get("commits") or fetch_push_commits(repo, before, head)

    events: list[dict[str, Any]] = []
    for commit in commits:
        sha, message, author = commit_details(commit)
        if not sha:
            continue
        event = build_event(
            event_id=f"github-commit-{sha}",
            device_id=device_id,
            source=SOURCE,
            event_type=EVENT_TYPE,
            observed_at=observed_at,
            payload={
                "timestamp": observed_at,
                "platform": "github",
                "actionType": "commit",
                "target": repo,
                "metadata": {
                    "repo": repo,
                    "sha": sha,
                    "message": message,
                    "authorName": author.get("name"),
                    "authorEmail": author.get("email"),
                    "actor": actor,
                    "ref": ref,
                    "head": head,
                    "before": before,
                    "commitUrl": commit.get("html_url") or commit.get("url"),
                    "username": username,
                },
            },
            attributes={
                "provider": "github-user-events",
                "username": username,
                "eventType": "PushEvent",
            },
        )
        events.append(event)
    return events


def main() -> None:
    args = parse_args()
    sync_github_events(
        username=args.username,
        bootstrap_servers=args.bootstrap_servers,
        device_id=args.device_id,
        state_file=args.state_file,
        limit=args.limit,
    )


def sync_github_events(*, username: str, bootstrap_servers: str, device_id: str, state_file: str, limit: int) -> int:
    if not username:
        raise RuntimeError("Set GITHUB_USERNAME or pass --username.")

    state = load_json_file(state_file, default={"published_ids": []})
    published_ids = set(state.get("published_ids", []))

    events = fetch_events(username, limit)
    push_events = [item for item in events if item.get("type") == "PushEvent"]

    new_commit_events: list[dict[str, Any]] = []
    for item in reversed(push_events):
        for commit_event in commit_events(item, username, device_id):
            if commit_event["eventId"] not in published_ids:
                new_commit_events.append(commit_event)

    if not new_commit_events:
        print("No new GitHub commit events to publish.")
        return 0

    publisher = KafkaEventPublisher(bootstrap_servers)
    try:
        for event in new_commit_events:
            publisher.publish(TOPIC, event)
            published_ids.add(event["eventId"])
            print(f"Published GitHub commit event {event['eventId']} to {TOPIC}")
    finally:
        publisher.close()

    save_json_file(state_file, {"published_ids": sorted(published_ids)})
    return len(new_commit_events)


if __name__ == "__main__":
    main()
