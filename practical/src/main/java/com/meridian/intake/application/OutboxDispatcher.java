package com.meridian.intake.application;
import com.meridian.intake.domain.Ports.DurableInboxOutbox;
import com.meridian.intake.domain.Ports.EventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
/** At-least-once delivery: a crash after publish but before mark can redeliver. */
@Service public class OutboxDispatcher {
  private final DurableInboxOutbox store;
  private final EventPublisher publisher;
  public OutboxDispatcher(DurableInboxOutbox store,EventPublisher publisher){this.store=store;this.publisher=publisher;}
  @Scheduled(fixedDelayString="${outbox.poll-ms:1000}")
  public synchronized int dispatchPending(){
    int sent=0;
    for(var event:store.pendingEvents()){
      try {publisher.publish(event);store.markPublished(event.idempotencyKey());sent++;}
      catch(RuntimeException e){break;}
    }
    return sent;
  }
}
