---
inclusion: manual
---

# Wizard

Generate an interactive bash wizard that walks a human through steps only they can perform: provisioning infrastructure, setting up credentials or CI secrets, walking an unfamiliar third-party dashboard, or running a one-off migration or cutover.

Don't invoke this for steps the agent can perform itself.

## What a wizard is

A bash script that walks a human step by step through a manual procedure. It:
- Opens each URL
- Says exactly what to click and copy
- Captures the values
- Writes them where they belong (`.env`, GitHub secrets)
- Confirms at every stage
- Shows progress (how many stages are left)

## Process

### 1. Scope the procedure

Work out every manual step the human must take and every value that gets captured. Read the repo first:
- `.env`, `.env.example`, docker-compose, framework config
- `.github/workflows/*` (every `secrets.*` / `vars.*` reference)
- README setup instructions

Show the user the ordered list of stages and the values each produces. Confirm.

### 2. Map each stage's journey

For each stage, write the precise path: which URL, what to do there, where a value appears, which variable it fills. Never invent steps that may not exist — ask or check docs.

### 3. Author the wizard

Write a bash script with:
- Stage-by-stage progress
- Confirmation gates before irreversible actions
- Cross-platform URL opening
- Hidden secret entry
- `.env` upserts
- `gh secret`/`gh variable` writes where needed
- Closing summary

### 4. Verify and hand off

- `bash -n <script>` to syntax check
- `chmod +x <script>`
- Don't run it (it opens browsers and blocks on human input)
- Tell the user how to run it
