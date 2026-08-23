# Triage Labels

Mapping from canonical triage roles to GitHub label strings.

| Canonical Role     | GitHub Label       |
|--------------------|--------------------|
| `needs-triage`     | `needs-triage`     |
| `needs-info`       | `needs-info`       |
| `ready-for-agent`  | `ready-for-agent`  |
| `ready-for-human`  | `ready-for-human`  |
| `wontfix`          | `wontfix`          |

## Category labels

| Role           | GitHub Label   |
|----------------|----------------|
| `bug`          | `bug`          |
| `enhancement`  | `enhancement`  |

## Notes

- Every triaged issue carries exactly one category label and one state label.
- Labels use the canonical names directly (no custom mapping needed).
- Create these labels in GitHub before first use: `gh label create <name>`.
