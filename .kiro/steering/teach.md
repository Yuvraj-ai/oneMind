---
inclusion: manual
---

# Teach

Teach the user a new skill or concept over multiple sessions, using the current directory as a stateful teaching workspace.

## Teaching Workspace Structure

- `MISSION.md` — the reason the user is interested in the topic (grounds all teaching)
- `./reference/*.html` — cheat sheets, glossaries, reference algorithms (quick-reference format)
- `RESOURCES.md` — list of high-quality resources for grounding teaching
- `./learning-records/*.md` — key insights and non-obvious lessons (like ADRs for learning)
- `./lessons/*.html` — self-contained HTML lessons (the primary unit of teaching)
- `./assets/*` — reusable components shared across lessons (stylesheets, quiz widgets)
- `NOTES.md` — scratchpad for user preferences and working notes

## Philosophy

To learn deeply, the user needs:
- **Knowledge** — captured from high-quality, high-trust resources
- **Skills** — acquired through interactive lessons
- **Wisdom** — from real-world interaction

## Lessons

Each lesson is one self-contained HTML file, titled `0001-<dash-case-name>.html`. Should be:
- Beautiful, with clean typography (think Tufte)
- Short and completable quickly (stay within working memory)
- Directly tied to the mission
- In the user's zone of proximal development
- Built from reusable components in `./assets/`

## Zone of Proximal Development

Each lesson should challenge the user "just enough." Figure out their zone by:
- Reading their learning-records
- Considering their mission
- Teaching the most relevant thing that fits

## Skills vs Knowledge

- **Knowledge**: difficulty is the enemy (eats working memory)
- **Skills**: difficulty is the tool (effortful retrieval builds storage strength)

Use interactive feedback loops for skill acquisition: quizzes, real-world steps, immediate feedback.
