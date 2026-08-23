---
inclusion: manual
---

# Handoff

Write a handoff document summarising the current conversation so a fresh agent can continue the work. Save to a temporary location or the workspace.

## What to include

- Current state of the work
- Decisions made and their reasoning
- What remains to be done
- Suggested skills the next agent should use
- Any blockers or open questions

## Rules

- Do not duplicate content already captured in other artifacts (specs, plans, ADRs, issues, commits, diffs). Reference them by path or URL instead.
- Redact any sensitive information (API keys, passwords, PII).
- If the user passed arguments, treat them as a description of what the next session will focus on and tailor the doc accordingly.
- Keep it concise — the next agent needs orientation, not a transcript.
