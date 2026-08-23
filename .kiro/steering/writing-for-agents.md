---
inclusion: manual
---

# Writing For Agents

Reference for writing any document an agent consumes: a skill, an AGENTS.md, a doc reached by a pointer. The packaging differs; the writing does not.

## Context pointers

A **context pointer** is a reference that names out-of-context material and encodes the condition for reaching it. The pointer's wording decides when the agent reaches the material.

A pointer does two jobs: state what the material is, and list the branches that should trigger reaching it.

Rules:
- **Front-load the leading word** — the pointer does its triggering work at the start
- **One trigger per branch** — collapse synonyms for the same branch
- **Cut identity the body already carries**

## The two loads

Every document and pointer spends one of two budgets:
- **Context load** — cost of always-loaded material (tokens + attention every turn)
- **Cognitive load** — cost on the human (which documents exist, when to use each)

## Information hierarchy

1. **In-file step** — what the agent does, in order (primary tier)
2. **In-file reference** — consulted on demand
3. **Disclosed reference** — in a separate file, loaded via pointer

Push too little down and the top bloats; push too much and you hide needed material.

**Progressive disclosure**: move material down the ladder (behind a pointer) so the top stays legible. Inline what every branch needs; push what only some branches reach.

**Co-location**: keep a concept's definition, rules, and caveats under one heading rather than scattered.

## Steps and completion criteria

Every step ends on a **completion criterion** — the condition telling done from not-done.

Two properties:
- **Clarity**: can the agent tell done from not-done? Vague bounds invite premature completion.
- **Demand**: how much it requires. "Every modified model accounted for" forces thoroughness.

## Leading words

A **leading word** is a compact concept from the model's pretraining that anchors behaviour in the fewest tokens. Examples: "tight" (fast, deterministic, low-overhead), "red" (the loop goes red on the bug).

Rules:
- Prefer existing pretrained words over coinages (they recruit priors for free)
- Refactor verbose descriptions into leading words
- Avoid negation ("don't do X") — prompt the positive target instead

## Pruning

- Keep each meaning in a **single source of truth** (one-place edit)
- The environment (package.json, config files, --help) is a source of truth too — don't restate what the agent can look up
- Check every line for **relevance**: does it still bear on what the document does?
- Hunt **no-ops**: instructions the model already obeys by default pay load to say nothing
