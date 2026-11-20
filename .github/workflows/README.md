# GitHub Actions — CI/CD Workflows

## 1. Overview

This directory contains the GitHub Actions workflows developed for Part C of the Meridian Mutual technical assessment.

The workflows demonstrate automated quality validation, a proposed deployment promotion strategy, and a successfully executed deployment simulation.

**Assessment restriction:** No Azure resources or production services may be deployed.

## 2. Workflow Summary

| Workflow | Trigger | Purpose | Status |
|---|---|---|---|
| `ci.yml` | Pull requests and pushes to `main` or `develop` | Java, Terraform, and repository security validation | Executed successfully |
| `cd.yml` | Manual dispatch | Proposed DEV → TEST → PROD promotion architecture | Deliberately disabled |
| `cd-dry-run.yml` | Push to `develop` when configured paths change; manual dispatch subject to GitHub's default-branch rules | Simulated environment promotion | Executed successfully |

## 3. Continuous Integration — `ci.yml`

The CI pipeline contains three independent jobs.

### Java 21 Tests

- Checks out the repository.
- Configures Java 21 using Temurin.
- Enables Maven dependency caching.
- Executes `mvn clean verify` in `practical/`.
- Uploads Surefire reports, including when tests fail.

The latest locally verified test execution completed with:

- 9 tests executed
- 0 failures
- 0 errors
- 0 skipped

### Terraform Validation

Uses Terraform 1.10.5 to execute:

```bash
terraform -chdir=infra fmt -check -recursive
terraform -chdir=infra init -backend=false -input=false
terraform -chdir=infra validate -no-color
```

These checks validate formatting and configuration without provisioning Azure infrastructure.

### Security Checks

Uses Gitleaks to identify potentially exposed secrets in repository history.

This is a security control, not a complete application security assessment.

### CI Evidence

The GitHub Actions CI workflow was executed successfully during development.

The completed checks included:

- Java 21 tests
- Terraform formatting and validation
- Repository security scanning

The exact workflow run and commit SHA should be retained as submission evidence.

## 4. Continuous Delivery Design — `cd.yml`

This workflow documents a proposed environment promotion process:

**DEV → TEST → PROD**

The jobs use GitHub Environments and illustrate future deployment approval and validation stages.

The DEV job includes a proposed Azure authentication step using OpenID Connect (OIDC).

All deployment jobs are intentionally disabled:

```yaml
if: ${{ false }}
```

Therefore, this workflow does not authenticate to Azure or deploy resources.

Before enabling it, the team would need to implement real deployment steps, environment approvals, artifact verification, integration tests, rollback procedures, and appropriate security controls.

## 5. CD Simulation — `cd-dry-run.yml`

The dry-run workflow was created to validate promotion sequencing without accessing Azure.

It contains three dependent jobs:

1. **DEV:** Identifies the commit SHA and simulates deployment.
2. **TEST:** Validates the artifact identifier propagated from DEV.
3. **PROD:** Simulates the final promotion stage.

### Verified Execution

| Stage | Result |
|---|---|
| DEV — Simulated deployment | Passed |
| TEST — Promotion validation | Passed |
| PROD — Promotion validation | Passed |
| Overall workflow | Success |
| Execution time | Approximately 21 seconds |

The workflow verifies job dependencies and basic promotion sequencing.

It does not build or deploy a container, provision infrastructure, execute smoke tests, or validate rollback.

The PROD stage currently references `github.sha` rather than an output passed through TEST. For a production-grade promotion process, the same immutable artifact identifier must be explicitly propagated and verified across every environment.

## 6. Deployment Safety

No actual Azure deployment was performed.

The following capabilities remain outside the verified scope:

- Azure OIDC authentication.
- Terraform plan and apply execution.
- Container image build, registry push, and deployment.
- GitHub Environment approval enforcement.
- Integration and smoke testing in deployed environments.
- Automated rollback and production recovery.

## 7. Production Readiness Requirements

Before implementing real CD, the team must:

- Configure protected GitHub Environments and required reviewers.
- Establish least-privilege Azure identities and federated credentials.
- Configure secure Terraform remote state and environment isolation.
- Build and promote container images by immutable digest.
- Integrate the application with PostgreSQL, Key Vault, and Azure Service Bus.
- Add deployment verification, monitoring, and rollback controls.
- Review and pin third-party GitHub Actions to trusted commit SHAs.

These steps require authorization and are not part of the executed assessment.

## 8. Related Documentation

- `../../infra/README.md` — Terraform configuration and local validation.
- `../../docs/infra/AZURE_SERVICES.md` — Azure service selection and architectural considerations.
- `../../docs/design/SOLUTION_DESIGN.md` — Integration architecture.
- `../../SUBMISSION.md` — Final assessment evidence and limitations.