---
inclusion: manual
---

# Ask Matt

You don't remember every skill, so ask.

A **flow** is a path through the skills. Most paths run along one **main flow**, and two **on-ramps** merge onto it. Everything else is standalone, or a vocabulary layer that runs underneath.

## The main flow: idea -> ship

The route most work travels. You have an idea and want it built.

1. **grill-with-docs** sharpens the idea by interview. Start here whenever you are working in a working directory: it's stateful, retaining what it learns in `CONTEXT.md` and ADRs.

2. **Branch: can you settle every question in conversation?** If a question needs a runnable answer (state, business logic, a UI you have to see), detour through a prototype:
   - **prototype** to answer the question with throwaway code

3. **Branch: is this a multi-session build?**
   - **Yes** -> **to-spec** (turn the thread into a spec), then **to-tickets** to split it into tracer-bullet tickets, each declaring its **blocking edges**. On a local tracker that's one file per ticket under `.scratch/<feature>/issues/`, worked blockers-first. On a real tracker the edges become native blocking links.
   - **No** -> implement right here, in the same context window.

   Either way, implement by driving **tdd** internally (one red-green slice at a time).

### Context hygiene

Keep steps 1-3 in **one unbroken context window** (don't compact or clear until after to-tickets) so the grilling, spec, and tickets all build on the same thinking. Each implementation then starts fresh, working from the ticket.

## On-ramps

- **Bugs and requests piling up** -> **triage**. It moves issues through triage roles and produces agent-ready issues.
- **Something's broken** -> **diagnosing-bugs**. For the hard ones: the bug that resists a first glance.
- **A huge, foggy effort** -> **wayfinder**. Charts a shared map of decision tickets on the issue tracker.

## Codebase health

- **improve-codebase-architecture** surfaces deepening opportunities; picking one generates an idea you can take into the main flow at grill-with-docs.

## Vocabulary underneath

- **domain-modeling**: sharpen the project's domain language.
- **codebase-design**: the deep-module vocabulary (module, interface, depth, seam, adapter, leverage, locality).

## Standalone

- **prototype**: throwaway code that answers one design question.
- **research**: delegate reading legwork to a background agent.
- **tdd**: red-green-refactor for building features or fixing bugs.
