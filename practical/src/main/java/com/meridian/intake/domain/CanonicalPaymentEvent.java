package com.meridian.intake.domain;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
public record CanonicalPaymentEvent(UUID eventId, String schemaVersion, String eventType,
  String provider, String country, String providerEventId, String providerPaymentId,
  String paymentKey, String idempotencyKey, String policyReference, String status,
  String originalStatus, Amount amount, Instant occurredAt, Instant receivedAt,
  String correlationId, Map<String,String> metadata) {
  public record Amount(String value,String currency) {}
}
