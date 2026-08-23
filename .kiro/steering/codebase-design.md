---
inclusion: manual
---

# Codebase Design

Design **deep modules**: a lot of behaviour behind a small interface, placed at a clean seam, testable through that interface. Use this language and these principles wherever code is being designed or restructured.

## Glossary

Use these terms exactly: don't substitute "component," "service," "API," or "boundary."

**Module**: anything with an interface and an implementation. Scale-agnostic: a function, class, package, or tier-spanning slice.

**Interface**: everything a caller must know to use the module correctly: the type signature, invariants, ordering constraints, error modes, required configuration, and performance characteristics.

**Implementation**: what's inside a module, its body of code.

**Depth**: leverage at the interface. The amount of behaviour a caller can exercise per unit of interface they have to learn. **Deep** = large behaviour behind small interface. **Shallow** = interface nearly as complex as implementation.

**Seam** (Michael Feathers): a place where you can alter behaviour without editing in that place; where a module's interface lives.

**Adapter**: a concrete thing that satisfies an interface at a seam.

**Leverage**: what callers get from depth. More capability per unit of interface they learn.

**Locality**: what maintainers get from depth. Change, bugs, knowledge, and verification concentrate in one place.

## Deep vs shallow

```
Deep module = small interface + lots of implementation:
+---------------------+
|   Small Interface   |  <- Few methods, simple params
+---------------------+
|                     |
|  Deep Implementation|  <- Complex logic hidden
|                     |
+---------------------+

Shallow module (avoid) = large interface + little implementation:
+---------------------------------+
|       Large Interface           |  <- Many methods, complex params
+---------------------------------+
|  Thin Implementation            |  <- Just passes through
+---------------------------------+
```

## Principles

- **Depth is a property of the interface, not the implementation.** A deep module can be internally composed of small parts; they just aren't part of the interface.
- **The deletion test.** Imagine deleting the module. If complexity vanishes, it was a pass-through. If complexity reappears across N callers, it was earning its keep.
- **The interface is the test surface.** Callers and tests cross the same seam.
- **One adapter means a hypothetical seam. Two adapters means a real one.** Don't introduce a seam unless something actually varies across it.

## Designing for testability

1. **Accept dependencies, don't create them.**
2. **Return results, don't produce side effects.**
3. **Small surface area.** Fewer methods = fewer tests needed.
