package com.meridian.intake;
import com.meridian.intake.adapters.*;
import com.meridian.intake.application.*;
import com.meridian.intake.security.HmacVerifier;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.*;
class WebhookFlowTest {
  private static final String Q="qz_test_whsec_4f8a1c2e9b7d";
  private static final String A="andina_test_secret_7c3e19ab";
  private static byte[] sample(String path) throws Exception {
    try(var stream=WebhookFlowTest.class.getResourceAsStream("/providers/"+path)) {
      assertNotNull(stream,path);return stream.readAllBytes();
    }
  }
  private static Clock clock(String instant){return Clock.fixed(Instant.parse(instant),ZoneOffset.UTC);}
  @Test void quetzalOriginalSignatureAndTampering() throws Exception {
    byte[] body=sample("quetzal-mx/samples/01-charge-succeeded.json");
    String signature="t=1795888803,v1=2d07770582c02f15235ac4e027b7732beef86be103c574a1c3b988a4275f5031";
    var verifier=new HmacVerifier(clock("2026-11-28T18:00:03Z"));
    assertTrue(verifier.quetzal(body,signature,Q));
    assertTrue(verifier.quetzal(body,signature+",v1=0000",Q));
    assertFalse(verifier.quetzal((new String(body,StandardCharsets.UTF_8)+" ").getBytes(StandardCharsets.UTF_8),signature,Q));
    assertFalse(new HmacVerifier(clock("2026-11-29T18:00:03Z")).quetzal(body,signature,Q));
  }
  @Test void andinaOriginalSignatureAndTampering() throws Exception {
    byte[] body=sample("andina-co/samples/02-transaccion-aprobada.json");
    String ts="2026-11-28T14:03:11-05:00",signature="MFQjfviySi6CjqbWtQcy5spKtXxHIxAvmg5W3PTzPJk=";
    var verifier=new HmacVerifier(clock("2026-11-28T19:03:11Z"));
    assertTrue(verifier.andina(body,ts,signature,A));
    assertFalse(verifier.andina(body,ts,signature+"bad",A));
    assertFalse(new HmacVerifier(clock("2026-11-28T19:14:00Z")).andina(body,ts,signature,A));
  }
  @Test void andinaRetryWithNewNotificationIdIsOneBusinessEvent() throws Exception {
    var store=new InMemoryInboxOutbox();
    var c=clock("2026-11-28T19:05:00Z");
    var controller=new WebhookController(store,c,Q,A);
    assertEquals(200,controller.andina(sample("andina-co/samples/02-transaccion-aprobada.json"),"2026-11-28T14:03:11-05:00","MFQjfviySi6CjqbWtQcy5spKtXxHIxAvmg5W3PTzPJk=").getStatusCode().value());
    assertEquals(200,controller.andina(sample("andina-co/samples/03-transaccion-aprobada-reintento.json"),"2026-11-28T14:08:11-05:00","CNu6m5X6B9bMKTsi/IzZfGquIh/KdxTyesBtL4ax424=").getStatusCode().value());
    assertEquals(1,store.acceptedCount());
    assertEquals(1,store.pendingEvents().size());
  }
  @Test void outboxRetainsEventOnBrokerFailureAndRetries() throws Exception {
    var store=new InMemoryInboxOutbox();
    var event=new PaymentNormalizer().normalize("Quetzal",sample("quetzal-mx/samples/01-charge-succeeded.json"),Instant.parse("2026-11-28T18:00:03Z"));
    store.saveIfAbsent(event.idempotencyKey(),"fingerprint",event);
    var broker=new StubEventPublisher();var dispatcher=new OutboxDispatcher(store,broker);
    broker.failNextPublish();assertEquals(0,dispatcher.dispatchPending());
    assertEquals(1,store.pendingEvents().size());
    assertEquals(1,dispatcher.dispatchPending());
    assertEquals(0,store.pendingEvents().size());assertEquals(1,broker.published().size());
    assertEquals(0,dispatcher.dispatchPending());
  }
  @Test void invalidSignatureDoesNotPersist() throws Exception {
    var store=new InMemoryInboxOutbox();var controller=new WebhookController(store,clock("2026-11-28T18:00:03Z"),Q,A);
    assertEquals(401,controller.quetzal(sample("quetzal-mx/samples/01-charge-succeeded.json"),"t=1795888803,v1=bad").getStatusCode().value());
    assertEquals(0,store.acceptedCount());
  }
  @Test void moneyConversionAndStatuses() throws Exception {
    var normalizer=new PaymentNormalizer();
    var q=normalizer.normalize("Quetzal",sample("quetzal-mx/samples/01-charge-succeeded.json"),Instant.now());
    assertEquals("Captured",q.status());assertEquals("MXN",q.amount().currency());
    var a=normalizer.normalize("Andina",sample("andina-co/samples/01-transaccion-pendiente.json"),Instant.now());
    assertEquals("Pending",a.status());assertEquals("COP",a.amount().currency());
  }
  @Test void acceptedResponseContainsTransactionAndNormalizedStatus() throws Exception {
    var store=new InMemoryInboxOutbox();
    var controller=new WebhookController(store,clock("2026-11-28T19:03:11Z"),Q,A);
    var response=controller.andina(sample("andina-co/samples/02-transaccion-aprobada.json"),
        "2026-11-28T14:03:11-05:00","MFQjfviySi6CjqbWtQcy5spKtXxHIxAvmg5W3PTzPJk=");
    assertEquals(200,response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals("WEBHOOK_ACCEPTED",response.getBody().code());
    assertEquals("Captured",response.getBody().status());
    assertEquals("APROBADA",response.getBody().originalStatus());
    assertEquals("PENDING_PUBLICATION",response.getBody().processingStatus());
    assertNotNull(response.getBody().transactionId());
    assertFalse(response.getBody().duplicate());
    assertEquals("NOT_DETERMINED",response.getBody().statusChange());
  }
  @Test void duplicateResponseHasNoNewOutboxEvent() throws Exception {
    var store=new InMemoryInboxOutbox();
    var controller=new WebhookController(store,clock("2026-11-28T19:05:00Z"),Q,A);
    controller.andina(sample("andina-co/samples/02-transaccion-aprobada.json"),
        "2026-11-28T14:03:11-05:00","MFQjfviySi6CjqbWtQcy5spKtXxHIxAvmg5W3PTzPJk=");
    var duplicate=controller.andina(sample("andina-co/samples/03-transaccion-aprobada-reintento.json"),
        "2026-11-28T14:08:11-05:00","CNu6m5X6B9bMKTsi/IzZfGquIh/KdxTyesBtL4ax424=");
    assertEquals("WEBHOOK_DUPLICATE",duplicate.getBody().code());
    assertTrue(duplicate.getBody().duplicate());
    assertEquals("ALREADY_ACCEPTED",duplicate.getBody().processingStatus());
    assertEquals(1,store.acceptedCount());
  }
  @Test void rejectedSignatureReturnsBodyWithoutUntrustedTransactionData() throws Exception {
    var controller=new WebhookController(new InMemoryInboxOutbox(),
        clock("2026-11-28T18:00:03Z"),Q,A);
    var response=controller.quetzal(sample("quetzal-mx/samples/01-charge-succeeded.json"),"invalid");
    assertEquals(401,response.getStatusCode().value());
    assertEquals("INVALID_SIGNATURE",response.getBody().code());
    assertNull(response.getBody().transactionId());
  }

}
