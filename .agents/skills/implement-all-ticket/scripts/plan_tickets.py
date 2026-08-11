#!/usr/bin/env python3
"""Create a deterministic dependency plan for Markdown implementation tickets."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable


DEFAULT_ELIGIBLE_STATUS = "ready-for-agent"
ALREADY_DELIVERED_STATUSES = {
    "ready-for-human",
    "done",
    "completed",
    "complete",
    "implemented",
    "merged",
}
UNSATISFIED_STATUSES = {
    "blocked",
    "draft",
    "in-progress",
    "in progress",
    "todo",
    "to-do",
}
NO_DEPENDENCY_VALUES = {"", "-", "none", "n/a", "na", "无", "无依赖"}


@dataclass(frozen=True)
class Ticket:
    identifier: str
    title: str
    path: str
    status: str
    blocked_by_raw: str
    dependency_tokens: tuple[str, ...]


class PlanningError(Exception):
    """Raised when the ticket graph cannot be planned safely."""


def normalize_status(value: str) -> str:
    return re.sub(r"\s+", "-", value.strip().lower()).replace("_", "-")


def normalize_token(value: str) -> str:
    value = value.strip().lower()
    value = re.sub(r"[^a-z0-9]+", "-", value)
    return re.sub(r"-+", "-", value).strip("-")


def numeric_identifier(value: str) -> str | None:
    match = re.search(r"(?<!\d)(\d+)(?!\d)", value)
    if not match:
        return None
    return match.group(1).zfill(2) if len(match.group(1)) == 1 else match.group(1)


def parse_field(text: str, field: str) -> str:
    match = re.search(
        rf"(?im)^[ \t]*(?:\*\*)?{re.escape(field)}(?:\*\*)?[ \t]*:[ \t]*(?:\*\*)?[ \t]*(.*?)[ \t]*$",
        text,
    )
    return match.group(1).strip() if match else ""


def parse_title(text: str, path: Path) -> str:
    match = re.search(r"(?m)^\s*#\s+(.+?)\s*$", text)
    if match:
        return re.sub(r"^\s*\d+\s*(?:[-—:]\s*)?", "", match.group(1)).strip()
    return path.stem


def parse_identifier(path: Path, text: str) -> str:
    filename_match = re.match(r"^([A-Za-z0-9]+(?:[-_][A-Za-z0-9]+)*)", path.stem)
    if filename_match:
        prefix = filename_match.group(1)
        number = numeric_identifier(prefix)
        if number:
            return number
        return normalize_token(prefix)

    heading = re.search(r"(?m)^\s*#\s+([^\s—:-]+)", text)
    if heading:
        number = numeric_identifier(heading.group(1))
        return number or normalize_token(heading.group(1))
    return normalize_token(path.stem)


def is_ticket_path(path: Path, root: Path) -> bool:
    if path.name.lower() in {"spec.md", "context.md", "readme.md"}:
        return False
    relative_parts = {part.lower() for part in path.relative_to(root).parts[:-1]}
    if "issues" in relative_parts:
        return True
    text = path.read_text(encoding="utf-8")
    return bool(re.search(r"(?im)^\s*(?:\*\*)?Status(?:\*\*)?\s*:\s*", text)) and bool(
        re.search(
            r"(?im)^\s*(?:\*\*)?(?:Blocked by|What to build)(?:\*\*)?\s*:",
            text,
        )
    )


def discover_ticket_paths(input_path: Path) -> list[Path]:
    if input_path.is_file():
        return [input_path.resolve()]
    if not input_path.is_dir():
        raise PlanningError(f"Ticket root does not exist or is not a directory: {input_path}")

    issues_dir = input_path / "issues"
    search_root = issues_dir if issues_dir.is_dir() else input_path
    paths = sorted(path for path in search_root.rglob("*.md") if path.is_file())
    if search_root == input_path:
        paths = [path for path in paths if is_ticket_path(path, input_path)]
    return paths


def parse_ticket(path: Path, root: Path) -> Ticket:
    del root
    text = path.read_text(encoding="utf-8")
    status = normalize_status(parse_field(text, "Status"))
    if not status:
        raise PlanningError(f"Missing Status field: {path}")
    blocked_by_raw = parse_field(text, "Blocked by")
    identifier = parse_identifier(path, text)
    return Ticket(
        identifier=identifier,
        title=parse_title(text, path),
        path=str(path.resolve()),
        status=status,
        blocked_by_raw=blocked_by_raw,
        dependency_tokens=tuple(),
    )


def dependency_tokens(raw: str, tickets: Iterable[Ticket]) -> tuple[str, ...]:
    value = raw.strip()
    normalized_no_deps = {normalize_token(item) for item in NO_DEPENDENCY_VALUES}
    raw_no_deps = {item.casefold() for item in NO_DEPENDENCY_VALUES if item}
    normalized_value = normalize_token(value)
    if value.casefold() in raw_no_deps or value.startswith("无") or (
        normalized_value and normalized_value in normalized_no_deps
    ):
        return tuple()

    known = list(tickets)
    tokens: list[str] = []

    # Numeric ticket references are common in strings such as "01 — Domain blocks".
    for match in re.finditer(r"(?<!\d)(\d+)(?!\d)", value):
        candidate = match.group(1).zfill(2) if len(match.group(1)) == 1 else match.group(1)
        if candidate not in tokens:
            tokens.append(candidate)

    if tokens:
        return tuple(tokens)

    for ticket in known:
        aliases = {
            normalize_token(ticket.identifier),
            normalize_token(Path(ticket.path).stem),
            normalize_token(ticket.title),
        }
        if normalized_value in aliases:
            tokens.append(ticket.identifier)
            break

    if not tokens:
        for part in re.split(r"[,;]|\band\b", value, flags=re.IGNORECASE):
            candidate = normalize_token(part)
            if candidate:
                tokens.append(candidate)
    return tuple(dict.fromkeys(tokens))


def parse_tickets(paths: list[Path], root: Path) -> list[Ticket]:
    parsed = [parse_ticket(path, root) for path in paths]
    by_id: dict[str, Ticket] = {}
    for ticket in parsed:
        if ticket.identifier in by_id:
            raise PlanningError(
                "Duplicate ticket identifier "
                f"{ticket.identifier}: {by_id[ticket.identifier].path} and {ticket.path}"
            )
        by_id[ticket.identifier] = ticket

    result: list[Ticket] = []
    for ticket in parsed:
        result.append(
            Ticket(
                identifier=ticket.identifier,
                title=ticket.title,
                path=ticket.path,
                status=ticket.status,
                blocked_by_raw=ticket.blocked_by_raw,
                dependency_tokens=dependency_tokens(ticket.blocked_by_raw, parsed),
            )
        )
    return sorted(result, key=lambda ticket: (ticket.identifier, ticket.path))


def build_plan(tickets: list[Ticket], eligible_status: str) -> dict:
    by_id = {ticket.identifier: ticket for ticket in tickets}
    eligible_status = normalize_status(eligible_status)
    eligible = {ticket.identifier: ticket for ticket in tickets if ticket.status == eligible_status}

    dependencies: dict[str, set[str]] = {identifier: set() for identifier in eligible}
    satisfied: dict[str, list[str]] = {identifier: [] for identifier in eligible}

    for ticket in eligible.values():
        for dependency in ticket.dependency_tokens:
            if dependency not in by_id:
                raise PlanningError(
                    f"Ticket {ticket.identifier} references missing dependency {dependency!r}"
                )
            prerequisite = by_id[dependency]
            if prerequisite.status == eligible_status:
                dependencies[ticket.identifier].add(prerequisite.identifier)
            elif prerequisite.status in ALREADY_DELIVERED_STATUSES:
                satisfied[ticket.identifier].append(prerequisite.identifier)
            else:
                raise PlanningError(
                    f"Ticket {ticket.identifier} depends on {prerequisite.identifier}, "
                    f"which has non-delivered status {prerequisite.status!r}"
                )

    order: list[str] = []
    remaining = {identifier: set(deps) for identifier, deps in dependencies.items()}
    while remaining:
        ready = sorted(identifier for identifier, deps in remaining.items() if not deps)
        if not ready:
            cycle = ", ".join(sorted(remaining))
            raise PlanningError(f"Dependency cycle detected among eligible tickets: {cycle}")
        order.extend(ready)
        for identifier in ready:
            remaining.pop(identifier)
        for deps in remaining.values():
            deps.difference_update(ready)

    ticket_rows = []
    for ticket in tickets:
        ticket_rows.append(
            {
                **asdict(ticket),
                "dependency_tokens": list(ticket.dependency_tokens),
                "eligible": ticket.identifier in eligible,
            }
        )

    return {
        "eligible_status": eligible_status,
        "tickets": ticket_rows,
        "order": [
            {
                "identifier": identifier,
                "path": by_id[identifier].path,
                "title": by_id[identifier].title,
                "status": by_id[identifier].status,
                "blocked_by": sorted(dependencies[identifier]),
                "already_delivered_prerequisites": sorted(satisfied[identifier]),
            }
            for identifier in order
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ticket_root", type=Path, help="Ticket directory or a single ticket Markdown file")
    parser.add_argument(
        "--status",
        default=DEFAULT_ELIGIBLE_STATUS,
        help=f"Status to dispatch (default: {DEFAULT_ELIGIBLE_STATUS})",
    )
    parser.add_argument("--json", action="store_true", help="Print machine-readable JSON")
    args = parser.parse_args()

    try:
        root = args.ticket_root.resolve()
        paths = discover_ticket_paths(root)
        if not paths:
            raise PlanningError(f"No ticket Markdown files found under {root}")
        tickets = parse_tickets(paths, root if root.is_dir() else root.parent)
        plan = build_plan(tickets, args.status)
    except (OSError, UnicodeError, PlanningError) as error:
        if args.json:
            print(json.dumps({"error": str(error)}, ensure_ascii=False, indent=2))
        else:
            print(f"ERROR: {error}", file=sys.stderr)
        return 2

    if args.json:
        print(json.dumps(plan, ensure_ascii=False, indent=2))
    else:
        print(f"Eligible status: {plan['eligible_status']}")
        if not plan["order"]:
            print("Dispatch order: (empty)")
        else:
            print("Dispatch order:")
            for index, item in enumerate(plan["order"], start=1):
                deps = ", ".join(item["blocked_by"]) or "none"
                delivered = ", ".join(item["already_delivered_prerequisites"])
                suffix = f"; already delivered: {delivered}" if delivered else ""
                print(f"{index}. {item['identifier']} ({item['status']}) <- {deps}{suffix}")
                print(f"   {item['path']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
