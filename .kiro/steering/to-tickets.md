---
inclusion: manual
---

# To Tickets

Break a plan, spec, or conversation into a set of **tickets**: tracer-bullet vertical slices, each declaring the tickets that **block** it.

The issue tracker configuration is in `docs/agents/issue-tracker.md`. If it doesn't exist, run the setup first.

## Process

### 1. Gather context

Work from whatever is already in the conversation context. If the user passes a reference (a spec path, an issue number or URL), fetch it and read its full body and comments.

### 2. Explore the codebase (optional)

If you have not already explored the codebase, do so. Ticket titles and descriptions should use the project's domain glossary vocabulary.

Look for opportunities to prefactor the code to make the implementation easier. "Make the change easy, then make the easy change."

### 3. Draft vertical slices

Break the work into **tracer bullet** tickets.

**Vertical slice rules:**
- Each slice cuts a narrow but COMPLETE path through every layer (schema, API, UI, tests): vertical, NOT horizontal
- A completed slice is demoable or verifiable on its own
- Each slice is sized to fit in a single fresh context window
- Any prefactoring should be done first

Give each ticket its **blocking edges**: the other tickets that must complete before it can start. A ticket with no blockers can start immediately.

**Wide refactors are the exception.** Sequence them as expand-contract: add the new form beside the old, migrate call sites in batches, then delete the old form.

### 4. Quiz the user

Present the proposed breakdown as a numbered list. For each ticket, show:
- **Title**: short descriptive name
- **Blocked by**: which other tickets must complete first
- **What it delivers**: the end-to-end behaviour this ticket makes work

Ask the user:
- Does the granularity feel right?
- Are the blocking edges correct?
- Should any tickets be merged or split further?

Iterate until the user approves.

### 5. Publish tickets

**Local files** -> write one file per ticket under `.scratch/<feature-slug>/issues/<NN>-<slug>.md`:

```markdown
# <NN>: <Ticket title>

**What to build:** the end-to-end behaviour this ticket makes work.

**Blocked by:** the numbers/titles of the tickets that gate this one, or "None (can start immediately)".

**Status:** ready-for-agent

- [ ] Acceptance criterion 1
- [ ] Acceptance criterion 2
```

**GitHub/GitLab** -> publish one issue per ticket in dependency order (blockers first). Use native blocking / sub-issue relationships. Apply the `ready-for-agent` triage label.

Work the **frontier**: any ticket whose blockers are all done.
