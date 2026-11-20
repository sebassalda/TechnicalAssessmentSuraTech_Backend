package com.meridian.intake.adapters;
import com.meridian.intake.domain.CanonicalPaymentEvent;
import com.meridian.intake.domain.Ports.DurableInboxOutbox;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
/** Demo adapter only: in-process atomicity, NOT crash durability. */
@Component @Profile("stub") public class InMemoryInboxOutbox implements DurableInboxOutbox {
  private final Map<String,String> fingerprints=new LinkedHashMap<>();
  private final Map<String,CanonicalPaymentEvent> pending=new LinkedHashMap<>();
  @Override public synchronized boolean saveIfAbsent(String key,String fingerprint,CanonicalPaymentEvent event) {
    String previous=fingerprints.get(key);
    if(previous!=null){if(!previous.equals(fingerprint))throw new IllegalStateException("Conflicting business event");return false;}
    fingerprints.put(key,fingerprint);pending.put(key,event);return true;
  }
  public synchronized List<CanonicalPaymentEvent> pendingEvents(){return List.copyOf(pending.values());}
  public synchronized void markPublished(String idempotencyKey){pending.remove(idempotencyKey);}
  public synchronized int acceptedCount(){return fingerprints.size();}
}
