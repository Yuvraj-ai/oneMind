---
inclusion: manual
---

# Diagnosing Bugs

A discipline for hard bugs. Skip phases only when explicitly justified.

When exploring the codebase, read `CONTEXT.md` (if it exists) to get a clear mental model of the relevant modules, and check ADRs in the area you're touching.

## Redact

**Redact every secret first**: write `<REDACTED>` in its place. Build loops against env vars, so the credential stays in the environment rather than in what you show.

## Phase 1: Build a feedback loop

**This is the skill.** Everything else is mechanical. If you have a **tight** pass/fail signal for the bug, you will find the cause.

Spend disproportionate effort here. **Be aggressive. Be creative. Refuse to give up.**

### Ways to construct one

1. **Failing test** at whatever seam reaches the bug: unit, integration, e2e.
2. **Curl / HTTP script** against a running dev server.
3. **CLI invocation** with a fixture input, diffing stdout against a known-good snapshot.
4. **Headless browser script** (Playwright / Puppeteer) that drives the UI.
5. **Replay a captured trace.** Save a real network request / payload / event log; replay it.
6. **Throwaway harness.** Spin up a minimal subset that exercises the bug code path.
7. **Property / fuzz loop.** Run 1000 random inputs and look for the failure mode.
8. **Bisection harness.** Automate `git bisect run`.
9. **Differential loop.** Run same input through old vs new and diff outputs.

### Tighten the loop

- Can I make it faster?
- Can I make the signal sharper?
- Can I make it more deterministic?

### Completion criterion: a tight loop that goes red

Phase 1 is done when the loop is **tight** and **red-capable**: one command that you have already run at least once, that is:
- **Red-capable**: drives the actual bug code path and asserts the user's exact symptom
- **Deterministic**: same verdict every run
- **Fast**: seconds, not minutes
- **Agent-runnable**: can run unattended

## Phase 2: Reproduce + minimise

Run the loop. Watch it go red. Confirm:
- The loop produces the failure mode the **user** described
- The failure is reproducible across multiple runs
- You have captured the exact symptom

Then shrink the repro to the **smallest scenario that still goes red**.

## Phase 3: Hypothesise

Generate **3-5 ranked hypotheses** before testing any.

Each hypothesis must be **falsifiable**: state the prediction it makes.

> "If <X> is the cause, then <changing Y> will make the bug disappear / <changing Z> will make it worse."

**Show the ranked list to the user before testing.**

## Phase 4: Instrument

Each probe must map to a specific prediction from Phase 3. **Change one variable at a time.**

Tool preference:
1. Debugger / REPL inspection
2. Targeted logs at boundaries
3. Never "log everything and grep"

**Tag every debug log** with a unique prefix, e.g. `[DEBUG-a4f2]`.

## Phase 5: Fix + regression test

Write the regression test **before the fix**, but only if there is a correct seam for it.

1. Turn the minimised repro into a failing test
2. Watch it fail
3. Apply the fix
4. Watch it pass
5. Re-run the Phase 1 feedback loop against the original scenario

## Phase 6: Cleanup

- [ ] Original repro no longer reproduces
- [ ] Regression test passes (or absence of seam is documented)
- [ ] All `[DEBUG-...]` instrumentation removed
- [ ] Throwaway prototypes deleted
- [ ] The hypothesis that turned out correct is stated in the commit message
