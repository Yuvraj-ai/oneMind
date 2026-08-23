---
inclusion: manual
---

# Prototype

A prototype is **throwaway code that answers a question**. The question decides the shape.

## Pick a branch

Identify which question is being answered:

- **"Does this logic / state model feel right?"** -> Build a single shareable HTML file (free-play buttons plus tabbed guided walkthroughs) that pushes the state machine through cases hard to reason about on paper.
- **"What should this look like?"** -> Generate several radically different UI variations on a single route, switchable via a URL search param and a floating bottom bar.

## Rules that apply to both

1. **Throwaway from day one, and clearly marked as such.** Locate the prototype code close to where it will actually be used. Name it so a casual reader can see it's a prototype, not production.

2. **Trivial to run.** A UI prototype starts from one command in the project's task runner. A logic demo is a single HTML file the user double-clicks.

3. **No persistence by default.** State lives in memory. Persistence is the thing the prototype is checking, not something it should depend on.

4. **Skip the polish.** No tests, no error handling beyond what makes the prototype runnable, no abstractions.

5. **Surface the state.** After every action, print or render the full relevant state so the user can see what changed.

6. **Capture it when done.** Fold any validated decision into the real code, then capture the prototype itself as a primary source: commit it to a throwaway branch, out of main. Capture the answer (the verdict and the question it settled) in the issue or a commit.
