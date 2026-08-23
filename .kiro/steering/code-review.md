---
inclusion: manual
---

# Code Review

Two-axis review of the diff between HEAD and a fixed point the user supplies:

- **Standards**: does the code conform to this repo's documented coding standards?
- **Spec**: does the code faithfully implement the originating issue / spec?

Both axes run as parallel evaluations so they don't pollute each other's context.

## Process

### 1. Pin the fixed point

Whatever the user said is the fixed point (a commit SHA, branch name, tag, `main`, `HEAD~5`, etc.). If they didn't specify one, ask for it.

Capture the diff: `git diff <fixed-point>...HEAD`. Also `git log <fixed-point>..HEAD --oneline`.

### 2. Identify the spec source

Look for the originating spec, in this order:
1. Issue references in commit messages (`#123`, `Closes #45`)
2. A path the user passed as an argument
3. A spec file under `docs/`, `specs/`, or `.scratch/` matching the branch/feature
4. If not found, ask the user. If there isn't one, skip the Spec axis.

### 3. Identify the standards sources

Look for: `CODING_STANDARDS.md`, `CONTRIBUTING.md`, or similar repo-level docs.

On top of repo standards, always apply the **smell baseline** (Fowler's code smells):

- **Mysterious Name**: name doesn't reveal what it does
- **Duplicated Code**: same logic shape in more than one place
- **Feature Envy**: method reaches into another object's data more than its own
- **Data Clumps**: same fields/params keep travelling together
- **Primitive Obsession**: primitive standing in for a domain concept
- **Repeated Switches**: same switch/if-cascade recurs
- **Shotgun Surgery**: one logical change forces scattered edits
- **Divergent Change**: one file edited for several unrelated reasons
- **Speculative Generality**: abstraction added for needs the spec doesn't have
- **Message Chains**: long a.b().c().d() navigation
- **Middle Man**: class that mostly delegates
- **Refused Bequest**: subclass that ignores most of what it inherits

Rules:
- Repo standards override the baseline
- Each smell is a judgement call, never a hard violation
- Skip anything tooling already enforces

### 4. Run both reviews

**Standards review:** Per file/hunk, flag documented-standard violations (cite the rule) and baseline smells (name it, quote the hunk). Distinguish hard violations from judgement calls.

**Spec review:** Flag: (a) requirements missing or partial, (b) behaviour not asked for (scope creep), (c) requirements that look implemented but wrong. Quote the spec line for each finding.

### 5. Aggregate

Present under `## Standards` and `## Spec` headings. Do not merge or rerank findings.

End with a one-line summary: total findings per axis, worst issue within each.

## Why two axes

- Code that follows every standard but implements the wrong thing -> Standards pass, Spec fail.
- Code that does what the issue asked but breaks conventions -> Spec pass, Standards fail.
