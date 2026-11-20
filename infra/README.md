# Azure Infrastructure as Code — Terraform

## 1. Overview

This directory contains Terraform definitions for the proposed Azure infrastructure supporting the Meridian Mutual premium payment notification integration.

The configuration was developed as part of the technical assessment and refined through local formatting, provider compatibility checks, and Terraform validation.

**Important:** The infrastructure has not been deployed to Azure. Successful Terraform validation does not establish production readiness.

## 2. Infrastructure Scope

The configuration defines the following Azure components:

| Component | Purpose |
|---|---|
| Resource Group | Logical organization of infrastructure resources |
| Azure Container Apps | Proposed runtime for the Java webhook intake application |
| Container Apps Environment | Hosting environment for the application |
| Azure Service Bus Standard | Asynchronous messaging with dead-letter queue support |
| Azure Key Vault | Secure management of application secrets |
| User-assigned Managed Identity | Identity for accessing Azure services |
| Azure RBAC | Least-privilege access to messaging and secrets |
| Log Analytics | Centralized telemetry collection |
| Application Insights | Application observability |

The Terraform definitions also include configurable values for deployment environments and country-specific settings.

Azure API Management is considered an enterprise-managed dependency in the target architecture and is not provisioned by this Terraform configuration.

## 3. Directory Structure

```text
infra/
├── main.tf
├── variables.tf
├── outputs.tf
├── versions.tf
├── .terraform.lock.hcl
└── README.md
```

Additional environment-specific example variable files may be present in the repository.

- `main.tf` — Resource definitions and access configuration.
- `variables.tf` — Configurable infrastructure parameters.
- `outputs.tf` — Selected infrastructure outputs.
- `versions.tf` — Terraform and provider requirements.
- `.terraform.lock.hcl` — Provider dependency lock file.

The lock file should be version-controlled to support reproducible provider selection.

## 4. Prerequisites

For local validation:

- Terraform CLI compatible with the declared version constraints.
- Access to the repository source files.
- No Azure credentials required for formatting and offline-backend initialization.

Actual Azure deployment would require an authorized subscription, identity, resource permissions, approved configuration, and secure state management.

## 5. Local Validation

Run the following commands from the repository root:

```bash
terraform -chdir=infra fmt -check -recursive
terraform -chdir=infra init -backend=false
terraform -chdir=infra validate
```

If formatting changes are necessary:

```bash
terraform -chdir=infra fmt -recursive
```

The assessment included local Terraform validation using the AzureRM provider, including version 4.81.0.

An initial compatibility warning concerning Key Vault RBAC configuration was addressed by replacing the deprecated property with `rbac_authorization_enabled`.

Local validation was reported successful during development. The final committed configuration should be revalidated after any subsequent changes.

These commands do not create Azure resources.

## 6. Environment Configuration

The configuration supports environment and country parameterization through Terraform variables and example variable files.

For any future authorized deployment:

- Maintain separate environment-specific configurations.
- Use secure remote state with locking.
- Avoid committing real credentials or sensitive variable files.
- Use managed identities and approved secret references.
- Review resource names, regions, quotas, and service availability.
- Apply least-privilege access controls.

Environment examples are documentation inputs and should not be treated as production-ready configurations.

## 7. Messaging and Security

The proposed messaging architecture uses Azure Service Bus Standard.

The Terraform configuration includes a queue with dead-lettering capabilities and a maximum delivery count of 10.

A user-assigned managed identity and Azure role assignments are defined to support controlled access to messaging and Key Vault.

Key Vault is configured for RBAC-based authorization and purge protection.

**Implementation boundary:** The Java prototype currently uses a stub event publisher. Provisioning a Service Bus queue does not establish an operational connection between the application and Azure Service Bus.

## 8. Known Gaps Before Deployment

The configuration requires additional engineering before a real deployment.

### Database Integration

The Java application requires PostgreSQL, but the Terraform configuration does not provision or connect a production database.

A secure database endpoint, credentials, schema migration process, and backup strategy would be required.

### Container Registry and Networking

Container image registry authentication, image pull permissions, and private networking require completion and verification.

The APIM-to-Container Apps routing model must be validated within the target Azure environment.

### Secrets and Runtime Configuration

Production webhook secrets, database credentials, Key Vault secret references, and application environment configuration are not supplied as deployable production values.

### Messaging Integration

The Java stub publisher must be replaced or extended with an Azure Service Bus integration, including authentication, retry policies, telemetry, and failure recovery.

### Operational Controls

Alert rules, cost budgets, deployment safeguards, regional resilience, resource protection, and operational readiness testing require further work.

### Deployment Validation

No real Terraform plan against an authorized subscription, infrastructure apply, deployment smoke test, or rollback exercise has been performed.

## 9. CI/CD Integration

The GitHub Actions CI workflow validates Terraform formatting and configuration.

The repository also contains:

- A deliberately disabled CD promotion design.
- A successfully executed DEV → TEST → PROD simulation.

Neither workflow has deployed Azure resources.

See `../.github/workflows/README.md` for implementation details and validation evidence.

## 10. Related Documentation

- `../docs/infra/AZURE_SERVICES.md` — Azure service choices, trade-offs, and estimated costs.
- `../docs/design/SOLUTION_DESIGN.md` — Target integration architecture.
- `../practical/README.md` — Java implementation and local execution.
- `../SUBMISSION.md` — Final validation summary and known limitations.