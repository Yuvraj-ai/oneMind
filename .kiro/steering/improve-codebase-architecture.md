---
inclusion: manual
---

# Improve Codebase Architecture

Surface architectural friction and propose **deepening opportunities**: refactors that turn shallow modules into deep ones. The aim is testability and AI-navigability.

Use the codebase-design vocabulary (**module**, **interface**, **depth**, **seam**, **adapter**, **leverage**, **locality**) exactly in every suggestion.

## Process

### 1. Explore

**Scope before you scan: YAGNI.** Put extra weight on the parts of the codebase that have recently changed.

- If the user named a direction, take it.
- Otherwise, walk back commit history (`git log --oneline`) to find hot spots.

Read the project's domain glossary (`CONTEXT.md`) and any ADRs.

Then walk the codebase and note where you experience friction:
- Where does understanding one concept require bouncing between many small modules?
- Where are modules **shallow**, with an interface nearly as complex as the implementation?
- Where have pure functions been extracted just for testability, but the real bugs hide in how they're called?
- Where do tightly-coupled modules leak across their seams?
- Which parts are untested, or hard to test through their current interface?

Apply the **deletion test** to anything you suspect is shallow.

### 2. Present candidates

For each candidate, present:
- **Files**: which files/modules are involved
- **Problem**: why the current architecture is causing friction
- **Solution**: plain English description of what would change
- **Benefits**: explained in terms of locality and leverage
- **Before / After**: illustrating the shallowness and the deepening
- **Recommendation strength**: `Strong`, `Worth exploring`, or `Speculative`

End with a **Top recommendation**: which candidate you'd tackle first and why.

Ask the user: "Which of these would you like to explore?"

### 3. Grilling loop

Once the user picks a candidate, grill through the decision tree: constraints, dependencies, the shape of the deepened module, what sits behind the seam, what tests survive.

Side effects happen inline:
- **Naming a deepened module after a concept not in `CONTEXT.md`?** Add the term.
- **Sharpening a fuzzy term?** Update `CONTEXT.md` right there.
- **User rejects the candidate with a load-bearing reason?** Offer an ADR.
