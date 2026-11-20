package com.meridian.intake.adapters;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.intake.domain.CanonicalPaymentEvent;
import com.meridian.intake.domain.Ports.DurableInboxOutbox;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed transactional inbox + outbox. Never stores raw webhook/PII. */
@Repository @Profile("postgres")
public class PostgresInboxOutbox implements DurableInboxOutbox {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  public PostgresInboxOutbox(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
  @Override @Transactional
  public boolean saveIfAbsent(String key,String fingerprint,CanonicalPaymentEvent event) {
    int inserted=jdbc.update("INSERT INTO intake_inbox(idempotency_key,fingerprint) VALUES (?,?) ON CONFLICT DO NOTHING",key,fingerprint);
    if(inserted==0){
      String previous=jdbc.queryForObject("SELECT fingerprint FROM intake_inbox WHERE idempotency_key=?",String.class,key);
      if(!fingerprint.equals(previous))throw new IllegalStateException("Conflicting business event");
      return false;
    }
    try {
      jdbc.update("INSERT INTO intake_outbox(idempotency_key,payload) VALUES (?,CAST(? AS jsonb))",key,json.writeValueAsString(event));
    }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Cannot serialize event",e);}
    return true;
  }
  @Override public List<CanonicalPaymentEvent> pendingEvents(){
    return jdbc.query("SELECT payload::text FROM intake_outbox WHERE published_at IS NULL ORDER BY id LIMIT 100",(rs,i)->{
      try{return json.readValue(rs.getString(1),CanonicalPaymentEvent.class);}
      catch(Exception e){throw new IllegalStateException("Invalid outbox payload",e);}
    });
  }
  @Override public void markPublished(String key){
    jdbc.update("UPDATE intake_outbox SET published_at=NOW() WHERE idempotency_key=? AND published_at IS NULL",key);
  }
  public long inboxCount(){return jdbc.queryForObject("SELECT COUNT(*) FROM intake_inbox",Long.class);}
  public long pendingCount(){return jdbc.queryForObject("SELECT COUNT(*) FROM intake_outbox WHERE published_at IS NULL",Long.class);}
  public long publishedCount(){return jdbc.queryForObject("SELECT COUNT(*) FROM intake_outbox WHERE published_at IS NOT NULL",Long.class);}
}
