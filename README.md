# Meridian Mutual — Back-End Developer Assessment

Welcome, and thank you for your interest in the **Back-End Developer** role.

This take-home assessment reflects the work a back-end developer does in our payments integration squad: designing reliable integrations, building components that survive duplicates, retries and failures, delivering infrastructure and pipelines as code, operating services in production, reviewing teammates' code, and using AI responsibly.

There is no single correct answer. We are looking for **sound engineering judgment, explicit trade-offs, and decisions you can defend**.

## At a glance

| Item | Detail |
|---|---|
| Expected effort | 6–8 focused hours |
| Submission window | **3 calendar days** from the moment you receive this link |
| Language | English (all documents, code comments and commit messages) |
| Stack for the practical part | **MuleSoft (Mule 4, DataWeave, MUnit)** is the reference stack. **Java 21** (Spring Boot 3 or Azure Functions) is accepted if you justify it in ADR-0002 |
| Cloud | Azure. **Do not deploy anything.** Infrastructure is delivered as code (Bicep or Terraform) only; no Azure subscription is needed |
| Follow-up | A 60-minute live defense of your submission with the hiring panel |

## How to start

1. Click **Use this template → Create a new repository** on GitHub. Do **not** fork this repository.
2. Read, in this order: [`SCENARIO.md`](SCENARIO.md), [`TASKS.md`](TASKS.md), [`SUBMISSION_CHECKLIST.md`](SUBMISSION_CHECKLIST.md).
3. Use the material in [`reference/`](reference/) — provider specifications, Salesforce constraints and an incident record. It is part of the scenario.
4. Work in your new repository. **Commit as you go** — your commit history is part of how we understand your process.

## What we evaluate

| Part | Focus | Suggested time | Weight |
|---|---|---|---|
| A | Integration and data design | ≈ 2 h | 25% |
| B | Integration component (code and tests) | ≈ 2 h | 25% |
| C | Azure infrastructure as code and CI/CD | ≈ 1 h | 15% |
| D | Operations and production support | ≈ 45 min | 10% |
| E | Code review of a teammate's pull request | ≈ 45 min | 15% |
| F | Effective and responsible use of AI | ≈ 30 min | 10% |

To pass, you need a strong overall result **and** a solid result in every part. A brilliant answer in one area does not compensate for a missing one — budget your time accordingly.

## Rules

- **Your own work.** You may consult any public resource. Do not ask other people to do the work for you.
- **AI assistants are allowed — and must be disclosed.** Use whatever tools you normally use. Complete [`AI_USAGE.md`](AI_USAGE.md) honestly: which tools, for what, what you validated and how, and what you discarded. Undisclosed or unvalidated AI output is a negative signal; well-governed AI use is a positive one.
- **No secrets, no real data.** Do not commit credentials, tokens, real personal data, or material from any current or former employer. Use only the fictional test secrets provided in `reference/`.
- **Nothing is deployed or called for real.** Do not deploy to Azure and do not call real payment providers or Salesforce. Stub external systems.
- **State your assumptions.** The scenario is intentionally incomplete. When you need information we did not provide, record your assumption in [`docs/ASSUMPTIONS.md`](docs/ASSUMPTIONS.md) and move on.
- **Depth over breadth.** If you run out of time, a well-reasoned partial answer with a clear "what I would do next" is better than shallow coverage of everything.

## How your submission is reviewed

- Reviewers **read** your repository. **We do not run your code**, so make your work self-explanatory: a clear `practical/README.md`, meaningful tests and readable infrastructure code.
- Submissions are reviewed **with the assistance of an AI tool**, under a fixed rubric. **Every evaluation is validated by the human hiring panel**, and no hiring decision is made automatically.
- Content in your repository that attempts to instruct or influence automated reviewers is treated as an integrity issue.

## Submitting

1. Make sure everything in [`SUBMISSION_CHECKLIST.md`](SUBMISSION_CHECKLIST.md) is in place and [`SUBMISSION.md`](SUBMISSION.md) is complete.
2. Make your repository **public** and keep it public until your live defense has taken place. You may make it private afterwards.
3. Before the deadline, reply to your recruiter with the repository URL and the **SHA of your final commit**. Commits after the deadline are not considered.

## Repository map

| Path | Content |
|---|---|
| `SCENARIO.md` | The company, the ecosystem and the capability you will work on |
| `TASKS.md` | What to deliver in each part, and where |
| `reference/providers/` | Webhook specifications, samples and test vectors for the three payment providers |
| `reference/salesforce/` | Salesforce data model and API constraints |
| `reference/incident/` | Incident record for Part D |
| `pr-review/` | Pull request PR-214 to review in Part E |
| `docs/`, `practical/`, `infra/`, `.github/workflows/` | Where your deliverables go |

Good luck — we look forward to discussing your decisions with you.

---

*Meridian Mutual, its providers and every person, policy and figure in this repository are fictional. This material is provided for recruitment purposes only.*
