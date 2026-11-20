# Infrastructure Documentation

This directory contains the architectural documentation for Part C of the Meridian Mutual technical assessment.

## Documents

- [AZURE_SERVICES.md](AZURE_SERVICES.md) — Proposed Azure services, architectural trade-offs, service tiers, and cost considerations.
- [Terraform Implementation](../../infra/README.md) — Infrastructure definitions, local validation, security considerations, and known deployment gaps.
- [GitHub Actions](../../.github/workflows/README.md) — CI validation, disabled CD design, and simulated environment promotion.

## Validation Scope

Terraform configuration checks and GitHub Actions CI/CD simulation were performed during development.

No Azure resources were provisioned or deployed.

The infrastructure remains a proposed architecture requiring additional integration, security, and operational work before production adoption.