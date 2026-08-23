---
inclusion: manual
---

# Grill With Docs

A grilling session that also builds your project's domain model. This is the stateful version of grilling: it sharpens terminology and updates `CONTEXT.md` and ADRs inline as decisions land.

## How it works

This steering combines two disciplines:
1. **Grilling** - the relentless interview (see `#grilling`)
2. **Domain modeling** - actively building and sharpening the domain model (see `#domain-modeling`)

## When to use

Use this whenever you are **working in a working directory** and want to sharpen an idea. It retains what it learns in `CONTEXT.md` and ADRs.

If you don't have a working directory (just want to think through something), use grilling alone instead.

## Process

1. Start the grilling: ask hard questions, force precision, one round at a time.
2. As terms are resolved, update `CONTEXT.md` immediately.
3. When decisions crystallize that are hard-to-reverse and surprising, offer an ADR.
4. Stop when the idea is sharp enough to act on.

The paper trail it leaves (`CONTEXT.md` updates, ADRs) is what makes this better than bare grilling when a repo is present.
