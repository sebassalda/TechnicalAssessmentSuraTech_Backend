package com.meridian.intake.domain;
import java.util.List;
public final class Ports {
  private Ports() {}
  public interface DurableInboxOutbox {
    boolean saveIfAbsent(String idempotencyKey,String fingerprint,CanonicalPaymentEvent event);
    List<CanonicalPaymentEvent> pendingEvents();
    void markPublished(String idempotencyKey);
  }
  public interface EventPublisher { void publish(CanonicalPaymentEvent event); }
}
