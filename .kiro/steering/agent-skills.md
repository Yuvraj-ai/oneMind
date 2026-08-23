# Agent Skills

This project uses Matt Pocock's engineering skills, adapted as Kiro steering files.

## Issue tracker

Issues live in GitHub Issues (uses `gh` CLI). See `docs/agents/issue-tracker.md`.

## Triage labels

Default canonical labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

## Domain docs

Single-context layout: one `CONTEXT.md` at root + `docs/adr/` for ADRs. See `docs/agents/domain.md`.

## Available skills (invoke via `#` context key)

### User-invoked (you type them)

| Skill | What it does |
|-------|-------------|
| `#ask-matt` | Ask which skill or flow fits your situation. A router over all skills. |
| `#grill-with-docs` | Grilling session that builds your domain model, updating CONTEXT.md and ADRs inline. |
| `#grill-me` | Same relentless interview but stateless — no files created. Use when no working directory. |
| `#grilling` | The raw interview primitive: rounds, frontier, relentless questioning. |
| `#triage` | Move issues through triage roles on GitHub Issues. |
| `#to-spec` | Turn the current conversation into a spec published to the tracker. |
| `#to-tickets` | Break a plan into tracer-bullet tickets with blocking edges. |
| `#to-questionnaire` | Turn a decision into a questionnaire for someone who holds the missing knowledge. |
| `#implement` | Implement work from a spec or tickets using TDD, then run code-review. |
| `#wayfinder` | Plan huge foggy efforts as a shared map of decision tickets. |
| `#improve-codebase-architecture` | Scan for deepening opportunities, present candidates, grill through one. |
| `#prototype` | Build throwaway code to answer a design question. |
| `#handoff` | Compact the conversation into a handoff doc for another agent to continue. |
| `#teach` | Teach the user a concept over multiple sessions using the workspace as a stateful classroom. |

### Model-invoked (agent reaches for these when the task fits)

| Skill | What it does |
|-------|-------------|
| `#tdd` | Test-driven development: red-green-refactor, one vertical slice at a time. |
| `#diagnosing-bugs` | Disciplined diagnosis loop: feedback loop -> reproduce -> hypothesise -> instrument -> fix. |
| `#domain-modeling` | Challenge terms, sharpen language, update CONTEXT.md and ADRs. |
| `#codebase-design` | Deep-module vocabulary: module, interface, depth, seam, adapter, leverage, locality. |
| `#research` | Investigate a question against primary sources, write findings to a cited Markdown file. |
| `#code-review` | Two-axis review (Standards + Spec) of a diff against a fixed point. |
| `#resolving-merge-conflicts` | Resolve in-progress merge/rebase conflicts by intent, never abort. |
| `#wizard` | Generate an interactive bash wizard for steps only a human can perform. |
| `#writing-for-agents` | Reference for writing documents agents consume (skills, AGENTS.md, pointed-at docs). |

## The main flow: idea -> ship

1. `#grill-with-docs` (sharpen the idea)
2. `#prototype` if needed (answer a design question with throwaway code)
3. `#to-spec` (synthesize into a spec)
4. `#to-tickets` (break into vertical slices)
5. Implement each ticket using `#tdd`

## On-ramps

- Bugs piling up -> `#triage`
- Something's broken -> `#diagnosing-bugs`
- Huge foggy effort -> `#wayfinder`

## Reference files

- `docs/agents/issue-tracker.md` — where issues live and how to interact
- `docs/agents/triage-labels.md` — label vocabulary mapping
- `docs/agents/domain.md` — domain doc layout and consumer rules
- `CONTEXT.md` — project glossary (created when first term is resolved)
- `docs/adr/` — architecture decision records
