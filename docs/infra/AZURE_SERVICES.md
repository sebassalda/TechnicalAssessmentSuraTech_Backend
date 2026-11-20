# Part C — Azure services selection and cost rationale

**Scope:** Proposed Azure infrastructure for Meridian Mutual payment-notification intake. This is design/IaC documentation, **not evidence of deployment**. Part B uses a PostgreSQL-backed inbox/outbox and a **stub** publisher; provisioning Service Bus does not automatically connect the Java application to it. Salesforce processing is downstream and out of scope for Part B.

## Design constraints and assumptions

- Provider webhook endpoints for Quetzal (MX) and Andina (CO) must be acknowledged quickly and durably. Austral (CL) is part of the wider design, **not implemented in Part B**.
- Approximately 15,000 notifications/day normally; 120,000/day at month end; bursts up to 40 requests/second; about 3% duplicates (from `SCENARIO.md`). Accepted 2xx notifications must not be lost.
- Azure is single-region in the scenario. API Management is an **enterprise-mandated gateway**, assumed pre-existing/shared and **not provisioned** by this Terraform baseline.
- Environment and country variables generate distinct resource names. The example is a deployment unit, **not** proof that all three countries or environments have been deployed.
- Estimates below are **illustrative USD/month order-of-magnitude budgets, not current Azure price quotes**. Actual amounts depend on region, contract, free grants, usage, retention, network egress, and service configuration. Verify with the Azure Pricing Calculator before approval.

## Services and alternatives

| Service / IaC resource | Choice and SKU | Why / reliability | Security | Alternatives considered / cost reasoning |
|---|---|---|---|---|
| Azure Container Apps (`azurerm_container_app`) | Consumption workload profile; example 0.5 vCPU, 1 GiB, min 1/max 3 replicas | Runs Spring Boot intake; revision control and horizontal scaling | Internal ingress; user-assigned managed identity; no public app endpoint | AKS (more operational overhead); App Service (viable, but less event-oriented scaling). Min 1 replica trades idle cost for lower cold-start risk. |
| Azure Service Bus (`namespace`, `queue`) | **Standard**, one `premium-payment-events` queue | Durable asynchronous boundary; `max_delivery_count=10`, expiration dead-lettering, 14-day TTL, 1-minute lock | Scoped `Azure Service Bus Data Sender` RBAC for intake identity | Storage Queues (cheaper but fewer broker features); Event Grid (event routing rather than work-queue semantics); Premium (stronger isolation, higher fixed cost). **Standard does not guarantee a particular throughput or latency.** |
| Azure Key Vault | Standard, RBAC, purge protection and soft delete | Central place for provider HMAC secrets and other credentials | User-assigned identity with `Key Vault Secrets User`; avoid secret values in Terraform and Git | Container App secrets alone (less central governance); hardcoded secrets (rejected). Need to wire secret references at deployment. |
| User-assigned Managed Identity and RBAC | One identity, scoped role assignments | Avoids long-lived service credentials | Service Bus Data Sender at queue scope; Key Vault Secrets User at vault scope | Service principals with stored secrets (rejected). Container image pull role is **not yet configured**. |
| Log Analytics Workspace | PerGB2018, 30-day retention | Central logs for incident investigation and outbox monitoring | Restrict workspace access, avoid logging raw webhook bodies and sensitive fields | External log platform (extra integration); no central logging (rejected). Data ingestion is a key variable cost. |
| Application Insights | Workspace-based | Request/error/latency traces and dependency telemetry | Sampling, sensitive-field scrubbing, RBAC | Logs-only (less distributed tracing). Java agent/instrumentation still needs configuration. |
| Resource Group | Per environment/country | Ownership, tagging and lifecycle boundaries | Azure RBAC and policy | Shared RG (lower administrative overhead but weaker isolation). |
| Azure API Management | **Pre-existing enterprise gateway; not in Terraform** | External webhook exposure, policies, throttling, central ingress | TLS, rate limiting, IP restrictions where supported; gateway must preserve raw signed body | Direct public Container Apps ingress (rejected because scenario mandates APIM). Shared gateway cost must be allocated separately. |
| PostgreSQL | **Required by the current Java `postgres` profile, not provisioned by this Terraform baseline** | Transactional inbox/outbox ensures durable acceptance before 2xx | Private connectivity, TLS, managed identity or secret-based connection | Azure Database for PostgreSQL Flexible Server (recommended managed option); in-memory stub is for tests only. Database/HA/backups/networking must be designed before production deployment. |
| Container registry | **External/pre-existing, not provisioned** | Stores immutable image digests | Registry authentication and `AcrPull`/equivalent permission must be added | Azure Container Registry Basic/Standard; enterprise shared registry. |

## Message failure behavior

- Verify HMAC and normalize payload **before** durable acceptance.
- Persist inbox and outbox atomically in PostgreSQL. Only then acknowledge valid webhooks with HTTP 200 (including duplicate notifications and business status `Failed`).
- Dispatcher retries transient publishing errors. A failed broker publish **after durable commit** does not retroactively make the webhook acceptance unsuccessful.
- Service Bus dead-letter queue is a **subqueue of the configured queue**, not a separate Terraform resource. `max_delivery_count=10` dead-letters messages that repeatedly fail delivery; message-expiration dead-lettering is enabled. Operational monitoring and replay procedures remain necessary.
- Quetzal requires a 2xx acknowledgement within 10 seconds; Andina requires HTTP 200 or 204 within 5 seconds. These limits require load testing, not just IaC validation.

## Indicative monthly cost envelope (USD)

Assume **one environment/country deployment**, East US 2 as a placeholder, approximately 450k normal notifications/month (15k × 30 days), a short end-of-month spike, low-to-moderate telemetry volume, one always-on 0.5 vCPU / 1 GiB container replica and occasional scaling. These figures are **planning ranges, not verified price quotes**:

| Component | Indicative USD/month | Notes |
|---|---:|---|
| Container Apps compute | $30–$120 | Strongly dependent on active replica seconds, consumption allowances and burst scaling |
| Service Bus Standard | $10–$50 | Base tier and operation volume; verify billable operations |
| Key Vault | $1–$10 | Low secret-operation volume assumed |
| Log Analytics + Application Insights | $10–$100 | Sampling, ingestion and retention dominate |
| Managed identity / resource group | $0 direct | Other related Azure services may still incur charges |
| **Subtotal of resources represented in baseline** | **$51–$280** | Rough budget only |

**Excluded and material:** PostgreSQL Flexible Server and storage/backups (potentially tens to hundreds of USD/month depending on HA/SKU), registry, shared API Management allocation (potentially significant), private endpoints/network egress, monitoring alerts, support and taxes. Consequently **$51–$280 is not an end-to-end system total**. Multiply/re-estimate for each environment/country and any shared-service allocations. For a credible final budget, price a concrete topology in the official Azure Pricing Calculator and record the quote date and region.

## Well-Architected considerations

- **Reliability:** transactional inbox/outbox, idempotency, broker DLQ, retries with backoff, backups and tested recovery; single-region deployment retains regional-outage risk.
- **Security:** HMAC verification, managed identity, least-privilege RBAC, internal ingress behind enterprise APIM, secret management, TLS and audit logs. Configure registry access and PostgreSQL connectivity before any real deployment.
- **Cost optimization:** Consumption compute with bounded replicas; telemetry sampling and retention; use Standard Service Bus until workload evidence justifies Premium; separate cost tags.
- **Operational excellence:** IaC review, environment isolation, observability, runbooks, alerting and controlled releases; Terraform validation alone is not a deployment test.
- **Performance efficiency:** scale based on observed request and queue metrics; load-test 40 req/s burst and provider acknowledgement deadlines; min 1 replica is an explicit latency/cost trade-off.

## Validation and remaining work

- User reported successful `terraform init -backend=false` and `terraform validate` with AzureRM v4.81.0, **before** the Key Vault property rename; Terraform reported one deprecation warning.
- This package changes `enable_rbac_authorization` to `rbac_authorization_enabled`. **Run `terraform fmt -recursive` and `terraform validate` again locally** to confirm warning-free validation. No `terraform plan` or `terraform apply` is claimed.
- Prior to a deployable solution: provision/attach PostgreSQL, configure registry authentication, wire Key Vault secrets and database settings into Container Apps, define APIM routing, set network controls, configure Java telemetry, add operational alerts and verify actual cost/quotas.
- Next Part C deliverable: CI on PRs and a **defined but not executed** CD workflow dev → test → prod with federated OIDC authentication and approvals.
