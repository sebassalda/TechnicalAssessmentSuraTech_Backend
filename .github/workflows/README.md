# Part C — GitHub Actions pipelines

Source: `TASKS.md`, Part C. **Do not deploy.**

## CI (`ci.yml`)
Runs on pull requests and pushes to `main` / `develop`:
- Java 21 Maven `clean verify`, Surefire reports uploaded even if tests fail.
- Terraform `fmt -check`, offline-backend `init` and `validate` using Terraform 1.10.5.
- Gitleaks scans repository history for accidental secrets.

CI is a proposed configuration; GitHub execution has not been verified. For reliable security, enable branch protection requiring all checks, code-owner reviews, dependency scanning and pinned action commit SHAs before production adoption. `gitleaks-action` licensing/configuration may require adjustment for the repository.

## CD (`cd.yml`) — design only, deliberately disabled
Manual workflow with sequential `dev → test → prod` jobs and GitHub Environments. Every job is disabled with `if: ${{ false }}` so **it cannot deploy**. The `dev` job demonstrates OIDC-based Azure authentication without client secrets. The later jobs show review and promotion gates. This is **not** an operational deployment pipeline: actual `terraform plan`, reviewed `apply`, image build/push, smoke tests, rollback and stage-specific OIDC authentication must be implemented and approved before enabling it.

For a future real CD, configure per-environment `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` as GitHub Environment variables; configure GitHub Environments protection/required reviewers; create Azure federated identity credentials scoped to each environment, with distinct least-privilege principals. Use remote Terraform state with secure locking and separate state per environment/country, review saved plan artifacts, and deploy images by immutable digest. Never store Azure client secrets or Terraform state in Git.

**Important:** Terraform currently defines a Container App but does not provision PostgreSQL, registry authentication, or Key Vault secret references. It is not deploy-ready. Part B publishes to a stub broker, not real Service Bus. Do not enable CD without closing these gaps.

### Continuous Delivery Validation

The CD dry-run workflow was successfully executed in GitHub Actions.

**Validation results:**
- DEV — Simulated deployment: Passed
- TEST — Promotion validation: Passed
- PROD — Promotion validation: Passed
- Overall workflow status: Success
- Total execution time: 21 seconds

The workflow validates sequential job execution and simulated artifact promotion without provisioning Azure resources.

The production CD design remains disabled to comply with the assessment's no-deployment requirement.

**Limitations:** This dry run does not validate actual Azure authentication, infrastructure provisioning, container deployment, environment approvals, or rollback execution.