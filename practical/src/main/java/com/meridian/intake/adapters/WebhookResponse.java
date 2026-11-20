package com.meridian.intake.adapters;

/** Safe, non-PII acknowledgment. Acknowledgment is not a Salesforce update. */
public record WebhookResponse(
    boolean success,
    String code,
    String message,
    String provider,
    String transactionId,
    String providerEventId,
    String paymentKey,
    String idempotencyKey,
    String status,
    String originalStatus,
    String processingStatus,
    String statusChange,
    boolean duplicate,
    String correlationId) {}
