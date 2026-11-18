# Submission checklist

Tick every item before you send your repository URL. Missing items are scored as missing.

## General
- [ ] `SUBMISSION.md` completed (including the final commit SHA you will send to your recruiter)
- [ ] `AI_USAGE.md` completed
- [ ] `docs/ASSUMPTIONS.md` lists every assumption you made
- [ ] No secrets, tokens or real personal data anywhere in the repository or its history
- [ ] Repository is **public**

## Part A — Integration and data design
- [ ] `docs/design/SOLUTION_DESIGN.md` (context diagram, sequence diagram, canonical event and mapping, idempotency and ordering, error handling, capacity, data, FR7)
- [ ] `docs/adr/0001-*.md` — Integration with Salesforce
- [ ] `docs/adr/0002-*.md` — Implementation stack and data store
- [ ] `docs/adr/0003-*.md` — optional

## Part B — Integration component
- [ ] `docs/api/webhooks.openapi.yaml`
- [ ] `docs/api/events/premium-payment.schema.json`
- [ ] `docs/api/README.md`
- [ ] Implementation in `practical/` for Quetzal Pagos (MX) and Andina Pay (CO)
- [ ] Automated tests in `practical/`
- [ ] `practical/README.md`

## Part C — Azure IaC and CI/CD
- [ ] Bicep or Terraform in `infra/`, with `infra/README.md`
- [ ] `docs/infra/AZURE_SERVICES.md`
- [ ] Pipeline definition in `.github/workflows/` or `pipelines/`

## Part D — Operations
- [ ] `docs/operations/OPERATIONS.md`
- [ ] `docs/operations/INC-0412-RESPONSE.md`

## Part E — Code review
- [ ] `pr-review/REVIEW.md`

## Part F — Responsible AI
- [ ] `AI_USAGE.md`
- [ ] `docs/ai/AI_GUIDELINES.md`
