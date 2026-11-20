package com.meridian.intake.adapters;
import com.meridian.intake.application.OutboxDispatcher;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
/** LOCAL-ONLY diagnostics; must not be enabled in any deployed environment. */
@RestController @RequestMapping("/local") @Profile("postgres")
public class LocalDiagnosticsController {
 private final PostgresInboxOutbox store;private final StubEventPublisher publisher;private final OutboxDispatcher dispatcher;
 public LocalDiagnosticsController(PostgresInboxOutbox store,StubEventPublisher publisher,OutboxDispatcher dispatcher){this.store=store;this.publisher=publisher;this.dispatcher=dispatcher;}
 @GetMapping("/status") public Map<String,Object> status(){return Map.of("inbox",store.inboxCount(),"pending",store.pendingCount(),"published",store.publishedCount(),"stubPublishedSinceStartup",publisher.published().size());}
 @PostMapping("/dispatch") public Map<String,Integer> dispatch(){return Map.of("dispatched",dispatcher.dispatchPending());}
 @PostMapping("/broker/fail-next") public Map<String,String> fail(){publisher.failNextPublish();return Map.of("result","next publish will fail");}
}
