from __future__ import annotations

import os
import re
from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal
from typing import Any
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import psycopg
from psycopg.rows import dict_row

try:
    from agents import RunContextWrapper
except ImportError:
    RunContextWrapper = Any


READ_ONLY_SQL_PATTERN = re.compile(r"^\s*(select|with)\b", re.IGNORECASE)
FORBIDDEN_SQL_PATTERN = re.compile(
    r"\b(insert|update|delete|drop|alter|create|grant|revoke|truncate|comment|copy|call|do|execute|vacuum|analyze|refresh|merge)\b",
    re.IGNORECASE,
)

DEFAULT_MAX_ROWS = 200
DEFAULT_STATEMENT_TIMEOUT_MS = 8000

SCHEMA_DESCRIPTION = """
Database schema available to the agent:

- known_places(id, name, category, loc geography(point, 4326), created_at, status)
- visits(id, place_id -> known_places.id, entry_time, exit_time)
- location_logs(id, timestamp, device_id, location_name, loc geography(point, 4326), visit_id -> visits.id)
- transaction_logs(id, extern_txn_id, timestamp, amount, category, visit_id -> visits.id)
- health_logs(id, timestamp, metric_type, val, unit, visit_id -> visits.id)
- dev_logs(id, timestamp, platform, action_type, target, metadata jsonb, visit_id -> visits.id)

Common joins:
- visits.place_id = known_places.id
- location_logs.visit_id = visits.id
- transaction_logs.visit_id = visits.id
- health_logs.visit_id = visits.id
- dev_logs.visit_id = visits.id

Important interpretation rules:
- Productivity questions usually map best to dev_logs activity patterns unless the user asks for a different measure.
- Spending questions usually map to transaction_logs.amount grouped by local weekday/time windows.
- Visit/location questions usually map to visits, location_logs, and known_places.
- All timestamps in the answer should be interpreted in the requested local timezone.
""".strip()


@dataclass
class DatabaseAgentContext:
    dsn: str
    timezone_name: str
    query_history: list[dict[str, Any]] = field(default_factory=list)
    max_rows: int = DEFAULT_MAX_ROWS
    statement_timeout_ms: int = DEFAULT_STATEMENT_TIMEOUT_MS


def _json_value(value: Any) -> Any:
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, datetime):
        return value.isoformat()
    if hasattr(value, "isoformat"):
        return value.isoformat()
    return value


def _serialize_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [{key: _json_value(value) for key, value in row.items()} for row in rows]


def _normalize_sql(sql: str) -> str:
    normalized = sql.strip()
    if not normalized:
        raise ValueError("SQL cannot be empty.")
    if normalized.endswith(";"):
        normalized = normalized[:-1].strip()
    if ";" in normalized:
        raise ValueError("Only a single SQL statement is allowed.")
    if not READ_ONLY_SQL_PATTERN.match(normalized):
        raise ValueError("Only read-only SELECT or WITH queries are allowed.")
    if FORBIDDEN_SQL_PATTERN.search(normalized):
        raise ValueError("The SQL query contains forbidden write or admin keywords.")
    return normalized


def _run_sql_query(
    *,
    dsn: str,
    sql: str,
    max_rows: int,
    statement_timeout_ms: int,
) -> tuple[list[dict[str, Any]], bool]:
    with psycopg.connect(dsn, row_factory=dict_row) as conn:
        with conn.cursor() as cursor:
            cursor.execute(f"SET LOCAL statement_timeout = {int(statement_timeout_ms)}")
            cursor.execute(sql)
            rows = cursor.fetchmany(max_rows + 1)

    truncated = len(rows) > max_rows
    if truncated:
        rows = rows[:max_rows]
    return _serialize_rows(rows), truncated


def generate_user_summary(
    *,
    question: str,
    dsn: str,
    timezone_name: str,
    model_name: str | None = None,
    max_rows: int = DEFAULT_MAX_ROWS,
    statement_timeout_ms: int = DEFAULT_STATEMENT_TIMEOUT_MS,
    max_turns: int = 8,
) -> dict[str, Any]:
    try:
        from agents import Agent, ModelSettings, Runner, function_tool
    except ImportError as exc:
        raise RuntimeError(
            "The OpenAI Agents SDK is not installed. Add `openai-agents` to the Python dependencies."
        ) from exc

    if not os.getenv("OPENAI_API_KEY"):
        raise RuntimeError("OPENAI_API_KEY is not configured for the Python agent.")

    try:
        ZoneInfo(timezone_name)
    except ZoneInfoNotFoundError as exc:
        raise ValueError(f"Unknown timezone: {timezone_name}") from exc

    context = DatabaseAgentContext(
        dsn=dsn,
        timezone_name=timezone_name,
        max_rows=max_rows,
        statement_timeout_ms=statement_timeout_ms,
    )

    @function_tool
    def describe_database_schema() -> dict[str, Any]:
        """Return the productivity tracker schema and guidance on how to interpret the tables."""
        return {
            "timezone": timezone_name,
            "schema": SCHEMA_DESCRIPTION,
        }

    @function_tool
    def query_productivity_database(
        wrapper: RunContextWrapper[DatabaseAgentContext],
        sql: str,
        reason: str = "",
    ) -> dict[str, Any]:
        """Run a single read-only SQL query against the productivity tracker database.

        Use this tool whenever you need facts from visits, places, location logs, transaction logs,
        health logs, or dev logs. Only SELECT and WITH queries are allowed.
        """

        normalized_sql = _normalize_sql(sql)
        rows, truncated = _run_sql_query(
            dsn=wrapper.context.dsn,
            sql=normalized_sql,
            max_rows=wrapper.context.max_rows,
            statement_timeout_ms=wrapper.context.statement_timeout_ms,
        )

        record = {
            "reason": reason,
            "sql": normalized_sql,
            "rowCount": len(rows),
            "truncated": truncated,
            "preview": rows[:5],
        }
        wrapper.context.query_history.append(record)

        return {
            "timezone": wrapper.context.timezone_name,
            "rowCount": len(rows),
            "truncated": truncated,
            "rows": rows,
        }

    instructions = f"""
You are a database analysis agent for a productivity tracker application.

Your job:
- Read the user's natural-language question.
- Use the database tools to gather only the facts you need.
- Analyze the data and answer the question clearly.

Rules:
- You must use the database tools before answering.
- Never invent facts that were not returned by the tools.
- If the user asks about "productivity", default to development activity in dev_logs unless the data strongly suggests another interpretation. State that assumption explicitly.
- Use the local timezone `{timezone_name}` when describing days or time windows.
- If the available data is insufficient, say exactly what is missing.
- Keep the final answer concise but evidence-based.
- Include the concrete day/time windows, counts, totals, or averages that support your conclusion.

Available schema:
{SCHEMA_DESCRIPTION}
""".strip()

    agent_kwargs: dict[str, Any] = {
        "name": "Productivity Database Analyst",
        "instructions": instructions,
        "tools": [describe_database_schema, query_productivity_database],
        "model_settings": ModelSettings(
            tool_choice="query_productivity_database",
            parallel_tool_calls=False,
        ),
    }
    if model_name:
        agent_kwargs["model"] = model_name

    agent = Agent(**agent_kwargs)
    result = Runner.run_sync(agent, question, context=context, max_turns=max_turns)

    final_output = result.final_output
    if final_output is None:
        final_output = ""
    elif not isinstance(final_output, str):
        final_output = str(final_output)

    return {
        "question": question,
        "timezone": timezone_name,
        "answer": final_output,
        "queries": context.query_history,
        "queryCount": len(context.query_history),
    }
