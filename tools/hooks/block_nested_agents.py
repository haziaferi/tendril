#!/usr/bin/env python3
"""PreToolUse guard: refuse to spawn a nested Claude session from inside this repository.

Why this exists
---------------
On 2026-09-22 a measurement harness ran `claude -p --allowedTools Read,Glob,Grep` against this
repo, on the stated belief that the flag made the child read-only. It does not. Five child
sessions ran Gradle builds, edited `shared/` domain code, removed a dependency from the version
catalogue, wrote a 169-line document and wrote to agent memory. One survived the first kill and was
still editing minutes later. Nothing in the session caught it: not the compiler, not a test, not a
re-read -- the only evidence was `git status` afterwards.

Every other failure that day was caught by a machine. This one was caught by luck, which is why it
is the one worth enforcing rather than documenting.

What it blocks
--------------
A Bash command that starts a non-interactive Claude session: `claude -p`, `claude --print`,
`claude --prompt`, or a pipe into a bare `claude`. Interactive `claude`, `claude --version` and
`claude mcp ...` are left alone, and `CLAUDE.md` is untouched because the match is case-sensitive.

It deliberately also fires on a *quoted* occurrence, because that is the shape the damage took:
`--cmd "claude -p --allowedTools Read"`. The cost is that grepping for the literal string trips it
-- accepted, and pinned as such in the tests. A false block costs one message and the person can
run the command themselves; a false allow reproduces an unsupervised agent editing the tree. The
asymmetry runs that way, not the other.

What to do instead
------------------
Run the work in this session, or sandbox the child in a throwaway `git worktree` so its writes
cannot reach the tree being reviewed.

Contract: stdin is the hook payload as JSON; exit 0 allows, exit 2 blocks and returns stderr to the
model. Any internal error allows -- a broken guard must not wedge the session.
"""

import json
import re
import sys

# 1. `claude` (its own word, optionally quoted) followed somewhere in the same command by -p /
#    --print / --prompt. `[^;&|]*?` keeps the flag inside one command rather than a later one.
# 2. A pipe into a bare `claude`, which reads the prompt from stdin.
# Case-sensitive on purpose: `CLAUDE.md` is a file here, not an invocation.
PATTERNS = (
    re.compile(r"""(?:^|[\s;&|("'`])claude(?:\.exe)?(?:\s+[^;&|]*?)?\s(?:-p|--print|--prompt)\b"""),
    re.compile(r"""\|\s*claude(?:\.exe)?\s*(?:$|[;&|])"""),
)

MESSAGE = """Blocked: this spawns a nested Claude session inside the Tendril repository.

`claude -p --allowedTools ...` does NOT restrict the child. It inherits full permissions and will
edit this tree: on 2026-09-22 five such sessions ran Gradle builds, edited shared/ domain code,
removed a dependency and wrote documents, while the parent reported the run as read-only.

Do the work in this session, or sandbox the child in a throwaway `git worktree` so its writes
cannot reach the tree under review. See CLAUDE.md, "Nested agents and harnesses"."""


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except (ValueError, OSError):
        return 0  # an unreadable payload is not grounds to wedge the session

    if payload.get("tool_name") != "Bash":
        return 0
    command = (payload.get("tool_input") or {}).get("command") or ""
    if not isinstance(command, str):
        return 0

    if any(p.search(command) for p in PATTERNS):
        sys.stderr.write(MESSAGE + "\n")
        return 2
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:  # noqa: BLE001 - a guard that crashes must still allow
        sys.stderr.write("block_nested_agents guard error (allowing): %r\n" % (exc,))
        sys.exit(0)
