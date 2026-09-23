"""The guard must block what actually happened, and nothing a normal session does.

A hook that blocks the wrong thing is worse than no hook, so both directions are pinned here and
run in CI beside the other `tools/tests`.
"""

import json
import subprocess
import sys
from pathlib import Path

GUARD = Path(__file__).resolve().parents[1] / "hooks" / "block_nested_agents.py"


def run(command, tool_name="Bash"):
    payload = json.dumps({"tool_name": tool_name, "tool_input": {"command": command}})
    proc = subprocess.run(
        [sys.executable, str(GUARD)], input=payload, capture_output=True, text=True, timeout=30
    )
    return proc.returncode, proc.stderr


BLOCKED = [
    # The command that actually did the damage, verbatim.
    'claude -p --allowedTools Read,Glob,Grep,Bash(git diff:*),Bash(git status:*)',
    "claude -p",
    "claude --print 'audit this'",
    "claude --prompt x",
    'python promptlab.py run --cmd "claude -p --allowedTools Read"',
    'echo "Reply PONG" | claude -p',
    "echo hi | claude",
    "cd /repo && claude -p 'go'",
]

ALLOWED = [
    # Interactive claude is the person's own tool, not a nested run.
    "claude",
    "claude --version",
    "claude mcp list",
    # The word appearing in other roles must not trip it.
    "git log --author=claude -p",  # `-p` is git's patch flag; `=claude` is not an invocation
    "cat CLAUDE.md",
    './gradlew --offline :app:testDebugUnitTest',
    "python tools/audit.py",
]


def test_blocks_nested_sessions():
    for cmd in BLOCKED:
        code, err = run(cmd)
        assert code == 2, "should have blocked: %s" % cmd
        assert "nested Claude session" in err


def test_allows_ordinary_commands():
    for cmd in ALLOWED:
        code, _ = run(cmd)
        assert code == 0, "should have allowed: %s" % cmd


def test_accepted_false_positives():
    """Grepping for the literal string trips the guard. Accepted, and pinned so it is a decision
    rather than a surprise: catching `--cmd "claude -p ..."` is worth one awkward grep."""
    code, _ = run("grep -rn 'claude -p' docs/")
    assert code == 2


def test_ignores_other_tools():
    code, _ = run("claude -p 'x'", tool_name="Read")
    assert code == 0


def test_malformed_payload_allows():
    proc = subprocess.run(
        [sys.executable, str(GUARD)], input="not json", capture_output=True, text=True, timeout=30
    )
    assert proc.returncode == 0
