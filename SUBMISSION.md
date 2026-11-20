# Technical Assessment Submission

**Project:** Meridian Mutual — Premium Payment Notifications  
**Assessment:** SuraTech Back-End Developer Technical Assessment

## Candidate Information

| Field | Value |
|---|---|
| Candidate ID | sebas.saldarriaga.v@gmail.com |
| Final commit SHA | TO BE COMPLETED |
| Submission date | November 20, 2026 |
| Time spent | 18 hrs |

## Solution Summary

This submission presents a payment notification integration architecture and a Java-based prototype focused on reliability, security, idempotency, and operational resilience.

The work covers architecture design, provider webhook processing, canonical event normalization, transactional inbox/outbox handling, Azure infrastructure definitions, CI/CD workflows, operational procedures, code review, and responsible AI usage.

The assessment was completed through iterative implementation, validation, troubleshooting, and refinement, with extensive AI assistance disclosed in `AI_USAGE.md`.

## Implementation and Validation

### Part A — Architecture

Developed and refined the proposed integration architecture, including canonical event contracts, idempotency, event ordering, failure handling, and architectural decisions.

The design separates webhook intake from asynchronous processing and Salesforce integration.

### Part B — Java Implementation

Integrated and validated the Java 21 / Spring Boot prototype for Quetzal Pagos and Andina Pay.

The implementation covers HMAC validation, payload normalization, duplicate detection, inbox/outbox persistence, stub publication, and structured webhook responses.

During testing, outdated HMAC fixtures caused authentication failures. After investigation and correction, the automated suite completed successfully.

**Verified Maven result:** 9 tests executed, 0 failures, 0 errors, 0 skipped.

### Part C — Infrastructure and CI/CD

Integrated Terraform infrastructure definitions and performed local configuration validation.

Corrected configuration issues identified during validation.

Executed GitHub Actions CI checks and validated a simulated DEV → TEST → PROD promotion sequence.

No real Azure deployment was performed.

### Part D — Operations

Prepared and refined monitoring guidance, operational responsibilities, incident response procedures, and DLQ recovery precautions.

The incident analysis distinguishes observed evidence from hypotheses and proposed remediation.

### Part E — Code Review

Completed the PR-214 review, identifying security, reliability, data consistency, and maintainability concerns.

The review provides prioritized findings and recommended corrective actions.

### Part F — Responsible AI

Documented the extensive use of AI, the iterative engineering workflow, validation evidence, corrected suggestions, and remaining limitations.

## Key Technical Decisions

- Preserve HMAC verification against the original webhook request bytes.
- Apply business-level idempotency rather than relying solely on notification identifiers.
- Use transactional inbox/outbox persistence to reduce event-loss risk.
- Separate webhook acknowledgment from downstream Salesforce processing.
- Protect payment state transitions from stale or out-of-order events.
- Validate CI/CD promotion behavior without deploying prohibited cloud resources.
- Distinguish implemented components from proposed production architecture.

## Evidence of Validation

| Area | Evidence |
|---|---|
| Java | Maven BUILD SUCCESS; 9 tests passed |
| Terraform | Local formatting and configuration validation |
| CI | Successful GitHub Actions checks |
| CD | Successful simulated DEV → TEST → PROD workflow |
| Local integration | Docker, PostgreSQL, and Postman checks during development |
| Documentation | Iterative review and corrections against assessment requirements |

## Known Limitations and Next Steps

The submission is an assessment prototype, not a production deployment.

Remaining work for production readiness includes:

1. Implementing and testing real Salesforce and broker integrations.
2. Expanding PostgreSQL and end-to-end integration test coverage.
3. Validating concurrency, event ordering, retries, and failure recovery under load.
4. Completing deployment-specific networking, identity, and secrets configuration.
5. Performing security, performance, and operational readiness reviews.

## Deliverable Locations

| Part | Path |
|---|---|
| A | `docs/design/`, `docs/adr/` |
| B | `practical/`, `docs/api/` |
| C | `infra/`, `.github/workflows/`, `docs/infra/` |
| D | `docs/operations/` |
| E | `pr-review/REVIEW.md` |
| F | `AI_USAGE.md`, `docs/ai/AI_GUIDELINES.md` |

For the original requirements, refer to `TASKS.md`, `SCENARIO.md`, and `SUBMISSION_CHECKLIST.md`.