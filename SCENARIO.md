# Scenario — Meridian Mutual: Premium Payment Notifications

> Meridian Mutual and everything described here are fictional.

## The company

Meridian Mutual is a digital-first insurer selling life, auto and home insurance in **Mexico, Colombia and Chile**. Each country has its own regulator, currency (MXN, COP, CLP), payment provider and business rules, but the technology organization is shared.

- Around 900,000 active policies across the three countries.
- Customers pay their premiums through each country's payment provider (cards, bank transfers and local payment methods).
- Engineering is organized in domain squads. You join the **Payments Integration** squad as a **Back-End Developer**.

## Current ecosystem

| Component | What it is | What you need to know |
|---|---|---|
| Cloud | Microsoft Azure | Primary cloud. One Azure region serves the three countries. Azure Monitor, Application Insights and a Log Analytics workspace already exist at platform level. |
| Integration platform | Enterprise integration platform with an **API Manager** | Every API exposed to channels, partners or providers must be published through the API Manager. Systems must not be called point to point. The platform can run integration applications (Mule 4 runtime) and can also front services that run on Azure. |
| Identity provider | OIDC / OAuth 2.0 identity provider | Issues tokens for customers and agents, and for **machine-to-machine** clients (client credentials). Internal APIs published in the API Manager require a token from this IdP. |
| Salesforce | **One Salesforce org** that hosts both the **CRM** and the **policy core** | System of record for customers, policies, premiums and payments. Contact-center agents work here. Availability ≈ **97%** per month. API usage is limited and shared across integrations. See [`reference/salesforce/`](reference/salesforce/). |
| Payment providers | One provider per country | **Quetzal Pagos** (Mexico), **Andina Pay** (Colombia), **Austral Pagos** (Chile). Each notifies payment events through **webhooks**, with its own payload format and signature scheme. See [`reference/providers/`](reference/providers/). Providers send payment tokens only: **no card data ever reaches Meridian**. |

## The capability: Premium Payment Notifications

### Today

Payments confirmed by the providers reach Salesforce through a **nightly file import** run by the operations team. A customer who pays the premium of a **suspended** policy waits up to 48 hours for the policy to be reinstated, and the contact center receives many complaints in the meantime.

The squad started a quick Java prototype to receive Chile's notifications in real time; its latest pull request is in [`pr-review/`](pr-review/). The prototype is **not** the target design.

### Goal

Process payment notifications from the three providers in near real time, reliably, and in a way that lets Meridian add new countries and providers without rewriting the flow.

### Functional requirements

| ID | Requirement |
|---|---|
| FR1 | Receive payment notifications from each country's provider through webhooks and verify their authenticity exactly as each provider specifies. |
| FR2 | Translate every provider notification into a single **canonical payment event**, regardless of country or provider. |
| FR3 | Record every payment in Salesforce as a `Premium_Payment__c` linked to its policy, with a status that follows the provider's lifecycle (pending, captured, failed, refunded). |
| FR4 | A payment must be recorded **exactly once in business terms**, even when providers send duplicates or retries. |
| FR5 | A late or out-of-order notification must never leave a payment in a state that contradicts the provider's latest real state (for example, a refunded payment shown as captured). |
| FR6 | Publish the canonical event for downstream consumers (Finance and Notifications). They subscribe on their own; you do not build them. |
| FR7 | Operations can find any notification by its provider identifiers and see its processing status (received, recorded, failed, dead-lettered) **without searching raw logs**. |

Policy reinstatement itself is **not** your responsibility: Salesforce automation reinstates a suspended policy when a captured payment covers its overdue amount (see [`reference/salesforce/DATA_MODEL.md`](reference/salesforce/DATA_MODEL.md)). The integration **must never** write the policy status directly.

### Non-functional requirements

| ID | Requirement |
|---|---|
| NFR1 | **Volume.** About 15,000 notifications per day across the three countries (Mexico ≈ 45%, Colombia ≈ 40%, Chile ≈ 15%). During the **month-end peak** (last two and first two days of each month) up to **120,000 per day**, with bursts of **40 per second** sustained for 15 minutes. About 3% of notifications are duplicates. |
| NFR2 | **Freshness.** 95% of payments visible in Salesforce within **5 minutes** of the provider's notification; 99.5% within **1 hour**. |
| NFR3 | **Durability.** No accepted notification may be lost. A notification is *accepted* when Meridian returns a `2xx` response to the provider. |
| NFR4 | **Provider timeouts.** Each provider expects a response within its own timeout and retries otherwise (see each specification). |
| NFR5 | **Salesforce constraints.** ≈ 97% availability; an API budget of **150,000 calls per rolling 24 hours** for this integration, within an org-wide limit shared with other integrations (see [`API_CONSTRAINTS.md`](reference/salesforce/API_CONSTRAINTS.md)). |
| NFR6 | **Security and privacy.** Secrets live in a secrets store, never in code or configuration files. Azure resources use managed identities with least privilege. Webhook endpoints are internet-facing through the API Manager. Payer personal data (names, national ID numbers, emails) must not appear in logs or telemetry, and each country's personal data protection law applies. |
| NFR7 | **Observability.** Every notification is traceable end to end with a correlation identifier, and freshness and loss are reported per country. |
| NFR8 | **Cost.** Choose and size services deliberately; justify the cost profile of your design. |
| NFR9 | **Extensibility.** Adding a fourth country or provider must not require changing the shared flow; provider-specific logic is isolated. |

## Glossary

| Term | Meaning |
|---|---|
| Suspended policy | A policy whose premium is more than 30 days overdue. Coverage is paused until the overdue amount is paid. |
| Reinstatement | Returning a suspended policy to active status. Performed by Salesforce automation, never by the integration. |
| Refund / reversal | Money returned to the payer after a capture (refund, reversal or chargeback, depending on the provider). |
| Canonical event | The single, provider-independent representation of a payment event that you define in Part A. |
