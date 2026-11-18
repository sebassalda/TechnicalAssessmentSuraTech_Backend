# Tasks

Suggested time per part is a guide, not a rule. Put each deliverable exactly where indicated — reviewers look for it there. Diagrams must be written in **Mermaid** inside Markdown so they can be read as text.

---

## Part A — Integration and data design (≈ 2 h)

Design the end-to-end solution for Premium Payment Notifications (FR1–FR7) for the **three** countries, within Meridian's existing ecosystem.

**Deliverables** in `docs/design/` and `docs/adr/`:

1. **`docs/design/SOLUTION_DESIGN.md`**, containing:
   - A **context diagram**: providers, API Manager, your components, the Azure services you use, Salesforce, downstream consumers, and the **trust boundaries**.
   - A **sequence diagram** for one notification, from the provider to Salesforce and to the canonical event, including the failure paths that matter (for example: invalid signature, duplicate, Salesforce unavailable, API limit reached).
   - The **canonical payment event**: its fields, and a table that maps each provider's payload and states to it.
   - Your **idempotency and ordering strategy**: the idempotency key for each provider, where it is enforced, and how late or out-of-order notifications are resolved (FR4, FR5).
   - Your **error-handling strategy**: what is retried, how and for how long; what goes to a dead-letter queue; how messages are replayed safely.
   - **Capacity**: show, with numbers, how the design handles the month-end peak (NFR1) within the Salesforce API budget and availability (NFR5), and how it meets NFR2.
   - **Data**: what you persist outside Salesforce, if anything — store, schema or document model, keys, indexes or partitioning, retention — and why.
   - How **operations** finds a notification and its status (FR7), and how that access is secured.
2. **Architecture Decision Records** using [`docs/adr/0000-template.md`](docs/adr/0000-template.md):
   - **ADR-0001 (mandatory): Integration with Salesforce.** Synchronous or asynchronous, per record or in batches, and how you protect the shared API budget and live with 97% availability.
   - **ADR-0002 (mandatory): Implementation stack and data store.** MuleSoft or an alternative for the integration, and the store for the state you keep (relational or non-relational). If you choose Java for Part B, this is where you justify it.
   - **ADR-0003 (optional):** the next most important decision in your design.

   Each ADR must show the options you considered, the trade-offs, and the consequences — including the negative ones.

---

## Part B — Integration component (≈ 2 h)

Implement the **intake** of payment notifications for **Mexico (Quetzal Pagos)** and **Colombia (Andina Pay)**: from the webhook request to the canonical event published on a queue or topic. Writing to Salesforce is **out of scope** for this part (you design it in Part A).

**Deliverables** in `docs/api/` and `practical/`:

1. **Contracts first:**
   - `docs/api/webhooks.openapi.yaml` — OpenAPI 3.x contract of the webhook endpoints you expose through the API Manager.
   - `docs/api/events/premium-payment.schema.json` — JSON Schema of your canonical event, including how its version is expressed.
   - `docs/api/README.md` — short notes on how the contracts evolve without breaking providers or consumers.
2. **Implementation** in `practical/`, conforming to your contracts and meeting **FR1, FR2, FR4, NFR3, NFR4 and NFR6** for the two providers.
   - Stack: Mule 4 (DataWeave, MUnit) as reference; Java 21 accepted with justification in ADR-0002.
   - Stub every external dependency (the message broker, any datastore, secrets). Behind a clear interface or connector configuration is enough; do not call real services.
   - Handle the error cases that matter, not only the happy path.
3. **Automated tests** (MUnit or JUnit 5) that prove the behaviors that matter most in this integration — beyond the happy path. Use the samples and test vectors in `reference/providers/`.
4. **`practical/README.md`**: the stack and versions, how to build and run the tests, what is stubbed, and the known gaps.

---

## Part C — Azure infrastructure as code and CI/CD (≈ 1 h)

**Do not deploy.** Deliver the code and the reasoning.

**Deliverables:**

1. **Infrastructure as code** in `infra/` (**Bicep or Terraform**) for the Azure resources of your design. At minimum: messaging (including dead-letter configuration), compute for anything you run on Azure, the secrets store, identities and role assignments with least privilege, and observability resources. Parameterize per environment (dev, test, prod) and per country where your design needs it. State in `infra/README.md` whether you validated it (`az bicep build`, `terraform validate`) and how.
2. **`docs/infra/AZURE_SERVICES.md`**: for each service — why it, which alternatives you discarded, tier or SKU, and how it supports reliability, security and cost (Azure Well-Architected Framework). Include an order-of-magnitude monthly cost estimate with its assumptions.
3. **CI/CD pipeline** as code, in `.github/workflows/` (GitHub Actions) or `pipelines/` (Azure Pipelines):
   - **CI** on every pull request, with the quality and security gates you consider essential (justify them).
   - **CD** through dev → test → prod with the controls you consider essential, including infrastructure changes and how the pipeline authenticates to Azure without stored secrets.

   CD stages are defined, **not executed**. A link to a successful CI run in `SUBMISSION.md` is welcome but optional (MuleSoft Enterprise dependencies may require credentials you do not have).

---

## Part D — Operations and production support (≈ 45 min)

**Deliverables** in `docs/operations/`:

1. **`OPERATIONS.md`** (max. 2 pages):
   - **SLIs and SLOs** per country, derived from NFR2 and NFR3.
   - **Instrumentation**: how the correlation identifier travels end to end, what you log, trace and measure — and what you must never log.
   - **Alerts**: which ones, thresholds, and who gets paged.
2. **`INC-0412-RESPONSE.md`** (max. 2 pages): your response to the incident in [`reference/incident/INC-0412.md`](reference/incident/INC-0412.md):
   - Your **first 30 minutes**: what you check, in what order, and why.
   - The **most likely root cause**, the evidence that supports it, and how you would confirm it.
   - **Remediation**, including how to reprocess the dead-lettered messages **safely**.
   - **Prevention**: what would have detected or avoided this earlier.

---

## Part E — Code review (≈ 45 min)

A teammate opened **PR-214** on the Java prototype. Read [`pr-review/PR-214.md`](pr-review/PR-214.md) and [`pr-review/PR-214.diff`](pr-review/PR-214.diff).

**Deliverable:** `pr-review/REVIEW.md`, using the template provided:

- Findings **prioritized by severity** (Blocker, Major, Minor, Nit), each with file and line, why it matters **in this scenario**, and a suggested fix.
- A **verdict** (approve, approve with comments, request changes) and a short summary addressed to the author.

Review as you would for a teammate: precise, prioritized and respectful.

---

## Part F — Effective and responsible use of AI (≈ 30 min)

**Deliverables:**

1. **`AI_USAGE.md`** (repository root, **mandatory**): the tools you used, for what, what you validated and how, and what you discarded and why. If you did not use AI, say so.
2. **`docs/ai/AI_GUIDELINES.md`** (max. 2 pages): the guidelines you would propose to the Payments Integration squad for using AI assistants in daily work. Cover at least:
   - What is allowed, and what data must never be shared with an AI tool.
   - How AI-generated code and tests are validated before merge.
   - How AI use is disclosed in pull requests.
   - **When one of our services calls an LLM** — for example, if operations asks for a feature that summarizes dead-lettered payment notifications with an LLM: the minimum controls you would require.
