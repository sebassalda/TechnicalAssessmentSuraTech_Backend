package com.meridian.intake.adapters;
import com.meridian.intake.domain.CanonicalPaymentEvent;
import com.meridian.intake.domain.Ports.EventPublisher;
import java.util.*;
import org.springframework.stereotype.Component;
/** Simulates a queue/topic; never calls an external broker. */
@Component public class StubEventPublisher implements EventPublisher {
  private final List<CanonicalPaymentEvent> messages=new ArrayList<>();
  private boolean failNext;
  @Override public synchronized void publish(CanonicalPaymentEvent event) {
    if(failNext){failNext=false;throw new IllegalStateException("Simulated broker outage");}
    messages.add(event);
  }
  public synchronized List<CanonicalPaymentEvent> published(){return List.copyOf(messages);}
  public synchronized void failNextPublish(){failNext=true;}
}
