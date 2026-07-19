#!/usr/bin/env python3
"""Fail closed on drift in the Mobile POS public/current documentation."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
CANONICAL_PLANS = (
    "docs/plans/ms2011_live_product_promotion_sync_plan.md",
    "docs/plans/ms2011_live_product_promotion_sync_implementation_plan.md",
)
CURRENT_PUBLIC = (
    "README.md",
    "BUILD_ENV.md",
    "AGENTS.md",
    "docs/ACTIVE_ITERATION.md",
    "docs/IMPLEMENTATION_STATUS.md",
    "docs/PROJECT_STATUS.md",
    "docs/plans/README.md",
    *CANONICAL_PLANS,
)
PLAN_REFERENCE_SURFACES = (
    "AGENTS.md",
    ".codex/agents/android-v2-domain-implementer.toml",
    ".codex/agents/pc-ms2011-readonly-implementer.toml",
    ".codex/agents/ms2011-safety-contract-reviewer.toml",
    ".codex/agents/pos-release-validator.toml",
    "docs/plans/ms2011_live_product_promotion_sync_implementation_plan.md",
)
HISTORICAL_EXCEPTIONS = {
    Path("docs/PROJECT_LOG.md"),
    Path("docs/archive/PROJECT_STATUS_2026-07-19.md"),
}
LEGACY_PLAN_PREFIXES = ("修改" + "方案/", "修改" + "方案\\")


def read(relative: str | Path) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def without_fenced_code(text: str) -> str:
    return re.sub(r"```.*?```|~~~.*?~~~", "", text, flags=re.DOTALL)


def markdown_files() -> list[Path]:
    return sorted(
        path
        for path in ROOT.rglob("*.md")
        if ".git" not in path.parts and path.name != "local-workspace.md"
    )


def check_markdown_links(failures: list[str]) -> int:
    checked = 0
    link_pattern = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
    for path in markdown_files():
        text = without_fenced_code(path.read_text(encoding="utf-8"))
        for raw_target in link_pattern.findall(text):
            target = raw_target.strip().split(maxsplit=1)[0].strip("<>")
            if not target or target.startswith(("#", "http://", "https://", "mailto:")):
                continue
            target = unquote(target.split("#", 1)[0].split("?", 1)[0])
            resolved = (path.parent / target).resolve()
            checked += 1
            try:
                resolved.relative_to(ROOT)
            except ValueError:
                failures.append(f"relative link escapes repository: {path.relative_to(ROOT)} -> {target}")
                continue
            if not resolved.exists():
                failures.append(f"broken relative link: {path.relative_to(ROOT)} -> {target}")
    return checked


def main() -> int:
    failures: list[str] = []

    missing = [path for path in CURRENT_PUBLIC + PLAN_REFERENCE_SURFACES if not (ROOT / path).is_file()]
    failures.extend(f"required file missing: {path}" for path in sorted(set(missing)))
    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1

    readme = read("README.md")
    h2 = re.findall(r"^##\s+(.+?)\s*$", readme, flags=re.MULTILINE)
    duplicates = sorted({heading for heading in h2 if h2.count(heading) > 1})
    if duplicates:
        failures.append(f"README duplicate H2 headings: {', '.join(duplicates)}")

    readme_forbidden = {
        "SHA-256": r"(?i)sha-?256",
        "exact byte count": r"(?i)\b\d[\d,]*\s*bytes?\b",
        "micro task ID": r"\b(?:MF|MB|CB)-\d+\b",
        "acceptance build claim": r"(?i)acceptance build",
        "local absolute path": r"(?i)(?:[A-Z]:\\|file://)",
    }
    for label, pattern in readme_forbidden.items():
        if re.search(pattern, readme):
            failures.append(f"README contains dynamic/private content: {label}")

    for plan in CANONICAL_PLANS:
        if not (ROOT / plan).is_file():
            failures.append(f"canonical plan missing: {plan}")

    for surface in PLAN_REFERENCE_SURFACES:
        text = read(surface)
        if any(prefix in text for prefix in LEGACY_PLAN_PREFIXES):
            failures.append(f"legacy plan path remains in active surface: {surface}")
        for plan in CANONICAL_PLANS:
            if plan not in text:
                failures.append(f"canonical plan reference missing in {surface}: {plan}")

    for path in markdown_files():
        relative = path.relative_to(ROOT)
        if relative in HISTORICAL_EXCEPTIONS:
            continue
        text = path.read_text(encoding="utf-8")
        if any(prefix in text for prefix in LEGACY_PLAN_PREFIXES):
            failures.append(f"legacy plan path remains outside preserved history: {relative}")

    sensitive_patterns = {
        "Windows absolute path": r"(?i)(?:^|[\s`'\"])[A-Z]:\\",
        "temporary Drive link": r"https://drive\.google\.com/",
        "dated business export": r"MS2011_PRODUCT_EXPORT_\d{8}",
        "dated database sample": r"AGT_MAIN_\d{8}\.db",
        "credential-like literal": r"(?i)(?:password|secret|token)\s*[:=]\s*['\"]?[A-Za-z0-9_./+\-]{8,}",
    }
    for relative in CURRENT_PUBLIC:
        text = read(relative)
        for label, pattern in sensitive_patterns.items():
            if re.search(pattern, text, flags=re.MULTILINE):
                failures.append(f"{relative} contains {label}")

    active = read("docs/ACTIVE_ITERATION.md")
    if len(re.findall(r"^- Stage:", active, flags=re.MULTILINE)) != 1:
        failures.append("ACTIVE_ITERATION must contain exactly one metadata Stage field")
    if len(re.findall(r"^## Next Exact Action\s*$", active, flags=re.MULTILINE)) != 1:
        failures.append("ACTIVE_ITERATION must contain exactly one Next Exact Action section")

    stage_markers = {
        "docs/ACTIVE_ITERATION.md": "- Stage: `S10/L3`",
        "docs/IMPLEMENTATION_STATUS.md": "| MS2011 foreground sync and order boundary | S10/L3 |",
        "docs/PROJECT_STATUS.md": "- 当前阶段：`S10/L3`",
        "docs/plans/README.md": "| [MS2011 implementation plan]",
    }
    for path, marker in stage_markers.items():
        text = read(path)
        if marker not in text or "S10/L3" not in text:
            failures.append(f"current stage marker missing or inconsistent: {path}")

    project_status_lines = len(read("docs/PROJECT_STATUS.md").splitlines())
    if project_status_lines > 60:
        failures.append(f"PROJECT_STATUS is too long: {project_status_lines} lines (max 60)")

    required_state = {
        "S10/L3": ("docs/ACTIVE_ITERATION.md", "docs/IMPLEMENTATION_STATUS.md", "docs/PROJECT_STATUS.md"),
        "USER_PAUSED": ("docs/ACTIVE_ITERATION.md", "docs/IMPLEMENTATION_STATUS.md", "docs/PROJECT_STATUS.md"),
        "MF-05": ("docs/ACTIVE_ITERATION.md", "docs/IMPLEMENTATION_STATUS.md", "docs/PROJECT_STATUS.md"),
        "MF-02": ("docs/ACTIVE_ITERATION.md", "docs/IMPLEMENTATION_STATUS.md", "docs/PROJECT_STATUS.md"),
        "MF-03": ("docs/ACTIVE_ITERATION.md", "docs/IMPLEMENTATION_STATUS.md", "docs/PROJECT_STATUS.md"),
        "G0B": ("docs/ACTIVE_ITERATION.md", "docs/IMPLEMENTATION_STATUS.md", "docs/PROJECT_STATUS.md"),
        "WRITE_CAPABILITY_PRESENT": (
            "docs/ACTIVE_ITERATION.md",
            "docs/IMPLEMENTATION_STATUS.md",
            "docs/PROJECT_STATUS.md",
        ),
    }
    for token, paths in required_state.items():
        for path in paths:
            if token not in read(path):
                failures.append(f"preserved state token {token} missing from {path}")

    checked_links = check_markdown_links(failures)

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        print(f"FAILED: {len(failures)} issue(s); checked {checked_links} relative Markdown link(s)")
        return 1

    print(f"PASS: document consistency checks; {checked_links} relative Markdown link(s) verified")
    print("PASS: current stage/state preserved; historical log and exact archive intentionally excluded from rewrite checks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
