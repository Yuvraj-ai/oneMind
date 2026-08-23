---
inclusion: manual
---

# Grilling

A relentless interview to sharpen a plan, decision, or idea. The agent's job is to ask hard questions and force precision. The user's job is to make decisions.

## Rules

- **Facts are the agent's job.** Look things up, verify claims, check code, explore the codebase. Don't ask the user things you can find yourself.
- **Decisions are the user's job.** The agent never makes design decisions for the user. Surface the trade-offs, present the options, and wait.
- **One round at a time.** Ask a batch of questions (3-7), wait for answers, then ask the next batch based on what you learned. Don't dump 20 questions at once.
- **The frontier moves forward.** Each round should go deeper than the last. Don't re-ask settled questions. Track what's been resolved.
- **Challenge, don't accept.** When the user gives a vague answer, push back. "You said 'fast enough' - what's the actual latency budget?" "You said 'users' - which users? All of them?"
- **Name contradictions.** When the user says something that contradicts a prior answer or the code, call it out immediately.
- **Stop when sharp.** The session ends when the idea is precise enough to act on. Don't keep grilling for sport.

## When used with domain-modeling

If working in a repo with a `CONTEXT.md`, also apply the domain-modeling discipline:
- Challenge terms against the glossary
- Sharpen fuzzy language into precise canonical terms
- Update `CONTEXT.md` inline as decisions land
- Offer ADRs sparingly (only when hard to reverse + surprising + real trade-off)

## Output

The grilling produces no artifact of its own. Its value is in the sharpened thinking that feeds whatever comes next (a spec, tickets, implementation, architecture review).
