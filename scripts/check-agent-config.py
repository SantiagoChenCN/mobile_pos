#!/usr/bin/env python3
"""Validate Mobile POS Codex agent configuration and safety invariants."""

from __future__ import annotations

import sys
import tomllib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / ".codex/config.toml"
AGENT_DIR = ROOT / ".codex/agents"
EXPECTED_PROFILES = {
    "android-v2-domain-implementer": {
        "file": "android-v2-domain-implementer.toml",
        "model": "gpt-5.6-terra",
        "effort": "medium",
        "sandbox": "workspace-write",
    },
    "pc-ms2011-readonly-implementer": {
        "file": "pc-ms2011-readonly-implementer.toml",
        "model": "gpt-5.6-terra",
        "effort": "medium",
        "sandbox": "workspace-write",
    },
    "ms2011-safety-contract-reviewer": {
        "file": "ms2011-safety-contract-reviewer.toml",
        "model": "gpt-5.6-sol",
        "effort": "high",
        "sandbox": "read-only",
    },
    "pos-release-validator": {
        "file": "pos-release-validator.toml",
        "model": "gpt-5.6-luna",
        "effort": "medium",
        "sandbox": "read-only",
    },
}
CANONICAL_PLANS = (
    "docs/plans/ms2011_live_product_promotion_sync_plan.md",
    "docs/plans/ms2011_live_product_promotion_sync_implementation_plan.md",
)
STANDARD_RETURN_FIELDS = (
    "STATUS",
    "SUMMARY",
    "CHANGED",
    "CONTRACTS",
    "VALIDATION",
    "EVIDENCE",
    "RISKS",
    "UNRESOLVED",
)
STATE_FILES = (
    "docs/ACTIVE_ITERATION.md",
    "docs/IMPLEMENTATION_STATUS.md",
    "docs/PROJECT_STATUS.md",
    "docs/PROJECT_LOG.md",
)
LEGACY_PLAN_PREFIXES = ("修改" + "方案/", "修改" + "方案\\")


def load_toml(path: Path, failures: list[str]) -> dict:
    try:
        with path.open("rb") as handle:
            return tomllib.load(handle)
    except (OSError, tomllib.TOMLDecodeError) as exc:
        failures.append(f"cannot parse TOML {path.relative_to(ROOT)}: {exc}")
        return {}


def main() -> int:
    failures: list[str] = []
    config = load_toml(CONFIG, failures)
    agents = config.get("agents", {})
    if agents.get("max_threads") != 4:
        failures.append(f"agents.max_threads must be 4, got {agents.get('max_threads')!r}")
    if agents.get("max_depth") != 1:
        failures.append(f"agents.max_depth must be 1, got {agents.get('max_depth')!r}")
    unexpected_config = set(config) - {"agents"}
    if unexpected_config:
        failures.append(f"unexpected project-wide config sections: {sorted(unexpected_config)}")

    profile_paths = sorted(AGENT_DIR.glob("*.toml"))
    if {path.name for path in profile_paths} != {spec["file"] for spec in EXPECTED_PROFILES.values()}:
        failures.append("profile file set does not match the four approved project profiles")

    parsed: dict[str, tuple[Path, dict]] = {}
    names: list[str] = []
    for path in profile_paths:
        data = load_toml(path, failures)
        name = data.get("name")
        if not isinstance(name, str) or not name:
            failures.append(f"profile has no non-empty name: {path.relative_to(ROOT)}")
            continue
        names.append(name)
        parsed[name] = (path, data)

    if len(names) != len(set(names)):
        failures.append("profile names are not unique")

    for name, expected in EXPECTED_PROFILES.items():
        if name not in parsed:
            failures.append(f"missing profile name: {name}")
            continue
        path, data = parsed[name]
        for key, value in (
            ("model", expected["model"]),
            ("model_reasoning_effort", expected["effort"]),
            ("sandbox_mode", expected["sandbox"]),
        ):
            if data.get(key) != value:
                failures.append(f"{path.name}: {key} must be {value!r}, got {data.get(key)!r}")
        if not isinstance(data.get("description"), str) or not data["description"].strip():
            failures.append(f"{path.name}: description is required")

        instructions = data.get("developer_instructions", "")
        if not isinstance(instructions, str) or not instructions.strip():
            failures.append(f"{path.name}: developer_instructions are required")
            continue

        if any(prefix in instructions for prefix in LEGACY_PLAN_PREFIXES):
            failures.append(f"{path.name}: legacy plan path remains")
        for plan in CANONICAL_PLANS:
            if plan not in instructions:
                failures.append(f"{path.name}: canonical plan reference missing: {plan}")
            elif not (ROOT / plan).is_file():
                failures.append(f"{path.name}: referenced plan does not exist: {plan}")

        if "STOP CONDITIONS" not in instructions:
            failures.append(f"{path.name}: STOP CONDITIONS section missing")
        for field in STANDARD_RETURN_FIELDS:
            if f"\n{field}\n" not in instructions:
                failures.append(f"{path.name}: standard return field missing: {field}")
        for state_file in STATE_FILES:
            if state_file not in instructions:
                failures.append(f"{path.name}: state-file modification prohibition incomplete: {state_file}")
        if "external system" not in instructions or "explicit user approval" not in instructions:
            failures.append(f"{path.name}: unauthorized real external-system prohibition missing")

    for name in ("pc-ms2011-readonly-implementer", "ms2011-safety-contract-reviewer"):
        if name not in parsed:
            continue
        path, data = parsed[name]
        instructions = data.get("developer_instructions", "")
        required_sql_safety = (
            "QueryId",
            "SELECT",
            "arbitrary SQL",
            "INSERT",
            "UPDATE",
            "DELETE",
            "MERGE",
            "EXEC",
            "DDL",
            "DBCC",
            "write-based permission tests",
            "MDF/LDF",
            "firewall",
        )
        for token in required_sql_safety:
            if token not in instructions:
                failures.append(f"{path.name}: SQL safety prohibition missing token: {token}")

    reviewer_text = parsed.get("ms2011-safety-contract-reviewer", (None, {}))[1].get(
        "developer_instructions", ""
    )
    if "never only on a subagent summary" not in reviewer_text:
        failures.append("safety reviewer must forbid summary-only PASS")
    if "no more than 5 findings" not in reviewer_text or "no more than 5 evidence" not in reviewer_text:
        failures.append("safety reviewer finding/evidence limits are missing")

    validator_text = parsed.get("pos-release-validator", (None, {}))[1].get(
        "developer_instructions", ""
    )
    for token in ("stage gate or release acceptance", "Do not run for ordinary tasks", "Do not install", "Do not edit"):
        if token not in validator_text:
            failures.append(f"release validator restriction missing: {token}")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        print(f"FAILED: {len(failures)} issue(s) across {len(profile_paths)} profile(s)")
        return 1

    print(f"PASS: agent config parsed; {len(profile_paths)} unique profile(s) validated")
    print("PASS: max_threads=4, max_depth=1, models/effort/permissions and safety invariants preserved")
    return 0


if __name__ == "__main__":
    sys.exit(main())
