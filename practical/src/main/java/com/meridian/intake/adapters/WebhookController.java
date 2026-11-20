package com.meridian.intake.adapters;

import com.meridian.intake.application.PaymentNormalizer;
import com.meridian.intake.domain.Ports.DurableInboxOutbox;
import com.meridian.intake.domain.CanonicalPaymentEvent;
import com.meridian.intake.security.HmacVerifier;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {
  private final HmacVerifier verifier;
  private final PaymentNormalizer normalizer = new PaymentNormalizer();
  private final DurableInboxOutbox store;
  private final Clock clock;
  private final String quetzalSecret, andinaSecret;

  public WebhookController(DurableInboxOutbox store, Clock clock,
      @Value("${webhooks.quetzal-secret:}") String quetzalSecret,
      @Value("${webhooks.andina-secret:}") String andinaSecret) {
    this.store = store;
    this.clock = clock;
    this.verifier = new HmacVerifier(clock);
    this.quetzalSecret = quetzalSecret;
    this.andinaSecret = andinaSecret;
  }

  @PostMapping("/quetzal")
  public ResponseEntity<WebhookResponse> quetzal(@RequestBody byte[] body,
      @RequestHeader(value = "Quetzal-Signature", required = false) String signature) {
    return receive("Quetzal", body, verifier.quetzal(body, signature, quetzalSecret));
  }

  @PostMapping("/andina")
  public ResponseEntity<WebhookResponse> andina(@RequestBody byte[] body,
      @RequestHeader(value = "X-Andina-Timestamp", required = false) String timestamp,
      @RequestHeader(value = "X-Andina-Signature", required = false) String signature) {
    return receive("Andina", body, verifier.andina(body, timestamp, signature, andinaSecret));
  }

  private ResponseEntity<WebhookResponse> receive(String provider, byte[] body, boolean authenticated) {
    // Do not parse or echo untrusted transaction identifiers before authentication.
    if (!authenticated) return ResponseEntity.status(401).body(error(provider,
        "INVALID_SIGNATURE", "Webhook signature is missing, invalid, or expired"));

    CanonicalPaymentEvent event;
    try {
      event = normalizer.normalize(provider, body, clock.instant());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(error(provider,
          "INVALID_PAYLOAD", "Webhook payload is malformed or contains unsupported values"));
    }

    String fingerprint = event.paymentKey() + "|" + event.status() + "|" +
        event.amount().value() + "|" + event.occurredAt() + "|" +
        event.policyReference() + "|" + event.metadata();
    try {
      boolean inserted = store.saveIfAbsent(event.idempotencyKey(), fingerprint, event);
      return ResponseEntity.ok().body(new WebhookResponse(true,
          inserted ? "WEBHOOK_ACCEPTED" : "WEBHOOK_DUPLICATE",
          inserted ? "Payment notification accepted and queued for processing" :
              "Duplicate business event acknowledged; no new outbox entry created",
          event.provider(), event.providerPaymentId(), event.providerEventId(),
          event.paymentKey(), event.idempotencyKey(), event.status(),
          event.originalStatus(), inserted ? "PENDING_PUBLICATION" : "ALREADY_ACCEPTED",
          "NOT_DETERMINED", !inserted, event.correlationId()));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(409).body(new WebhookResponse(false,
          "IDEMPOTENCY_CONFLICT", "An event with this idempotency key has different business data",
          event.provider(), event.providerPaymentId(), event.providerEventId(),
          event.paymentKey(), event.idempotencyKey(), event.status(),
          event.originalStatus(), "REJECTED", "NOT_DETERMINED", false, event.correlationId()));
    } catch (Exception e) {
      return ResponseEntity.status(503).body(new WebhookResponse(false,
          "PERSISTENCE_UNAVAILABLE", "Notification could not be durably accepted; retry later",
          event.provider(), event.providerPaymentId(), event.providerEventId(),
          event.paymentKey(), event.idempotencyKey(), event.status(),
          event.originalStatus(), "NOT_ACCEPTED", "NOT_DETERMINED", false, event.correlationId()));
    }
  }

  private WebhookResponse error(String provider, String code, String message) {
    return new WebhookResponse(false, code, message, provider, null, null, null,
        null, null, null, "REJECTED", "NOT_DETERMINED", false, null);
  }
}
