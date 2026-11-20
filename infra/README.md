# Part C — Terraform (step 1: initial infrastructure draft)

Source: TASKS.md, Part C. **Do not deploy.** This is a reviewable starting point, not a validated production deployment.

## Scope
- Resource group, Log Analytics, Application Insights
- Azure Service Bus Standard queue with maximum delivery count 10, message expiration dead-lettering and native DLQ
- User-assigned managed identity with queue-scoped Service Bus Data Sender and Key Vault Secrets User roles
- RBAC-enabled Key Vault with purge protection
- Internal-ingress Azure Container Apps environment and Java intake app
- dev/test/prod and country parameterization via variables and sample tfvars

## Local validation (no deployment)
```bash
cd infra
terraform init -backend=false
terraform fmt -check -recursive
terraform validate
```
Validation status: **EXECUTED BY USER** on Windows: `terraform fmt -recursive`, `terraform init -backend=false` (AzureRM v4.81.0), and `terraform validate` reported success with a Key Vault deprecation warning. The Key Vault property has since been updated to `rbac_authorization_enabled`; rerun validation after this edit to confirm a warning-free result. This is not an Azure deployment validation.

## Known gaps before deployment
1. PostgreSQL is required by the Java app but is not provisioned or connected here. The container is not operational until a secured DB endpoint, credentials and schema lifecycle are supplied.
2. Container registry image-pull permissions and private networking need design and implementation. The registry hostname is documented but not wired to registry authentication.
3. Runtime secret references, Key Vault values, HMAC secrets and database credentials are deliberately **not** committed; configure secure references before use.
4. Service Bus queue is provisioned, but Part B uses a stub publisher. No Azure Service Bus SDK integration is implied.
5. APIM is assumed to be enterprise-managed; no public ingress is exposed. Confirm private APIM-to-Container Apps routing.
6. Alert rules, monitoring thresholds, resource locks, cost budgets, regional failover and per-environment isolation policy are pending subsequent steps.
7. Country-specific resources should be deployed only when required by the architecture. Service names and availability must be verified in the target subscription.
8. Infrastructure state storage and deployment identities will be specified in the CI/CD step; never commit tfstate or real tfvars.

## CI/CD definitions
See `.github/workflows/README.md`. CI is defined but not yet executed on GitHub. CD stages are intentionally disabled and do not deploy.
