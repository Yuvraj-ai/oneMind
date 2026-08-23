---
inclusion: manual
---

# Wayfinder

Plan a huge chunk of work (more than one agent session can hold) as a shared map of decision tickets on your issue tracker, and resolve them one at a time until the way to the destination is clear.

The issue tracker configuration is in `docs/agents/issue-tracker.md`.

## Plan, don't do

Wayfinder is **planning** by default: each ticket resolves a decision, and the map is done when the way is clear, with nothing left to decide before someone goes and does the thing.

## The Map

The map is a single issue on the repo's issue tracker, labelled `wayfinder:map`, the canonical artifact. Its tickets are child issues of the map.

### Map body

```markdown
## Destination

<what reaching the end of this map looks like>

## Notes

<domain; skills every session should consult; standing preferences>

## Decisions so far

- [<closed ticket title>](link): <one-line gist of the answer>

## Not yet specified

<!-- fog of war: in-scope items you can't ticket yet -->

## Out of scope

<!-- work ruled beyond the destination -->
```

### Tickets

Each ticket is a child issue. Its body is the question, sized to one context window:

```markdown
## Question

<the decision or investigation this ticket resolves>
```

## Ticket Types

- **Research** (AFK): Reading documentation or resources to surface a fact.
- **Prototype** (HITL): Make a cheap, rough artifact to react to.
- **Grilling** (HITL): Conversation to sharpen a decision.
- **Task** (HITL or AFK): Manual work that must happen before a decision can be made.

## Fog of war

The map is deliberately incomplete. Beyond the live tickets lies fog: decisions you can tell are coming but can't yet pin down. Resolving a ticket clears the fog ahead of it.

**Fog or ticket?** Can you state the question precisely now?
- **Ticket when** the question is already sharp
- **Not yet specified when** you can't yet phrase it sharply

## Invocation

### Chart the map

1. Name the destination through grilling.
2. Map the frontier breadth-first.
3. Create the map issue.
4. Create the tickets you can specify now as child issues.
5. Fire research subagents for research tickets.
6. Stop: charting is one session's work.

### Work through the map

1. Load the map.
2. Choose the ticket (user-named or first frontier ticket).
3. Resolve it.
4. Record the resolution, close the issue, append to Decisions-so-far.
5. Add newly-surfaced tickets; graduate any fog the answer has made specifiable.

**Never resolve more than one ticket per session** (exception: research tickets).
