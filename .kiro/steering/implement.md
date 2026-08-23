---
inclusion: manual
---

# Implement

Implement a piece of work based on a spec or set of tickets.

## Process

1. Read the spec or ticket to understand what needs to be built.
2. Use `#tdd` where possible, at pre-agreed seams.
3. Run typechecking regularly, single test files regularly, and the full test suite once at the end.
4. Once done, use `#code-review` to review the work.
5. Commit your work to the current branch.

## Rules

- Implement what the spec says. Do not add features that are not in the spec.
- Use the project's domain vocabulary from `CONTEXT.md`.
- Respect ADRs in `docs/adr/`.
- Work in vertical slices — one complete behaviour at a time.
- Each slice should leave the codebase in a working state (tests pass, types check).
