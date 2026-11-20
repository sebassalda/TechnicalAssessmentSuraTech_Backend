package com.meridian.intake.application;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.intake.domain.CanonicalPaymentEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
public final class PaymentNormalizer {
  private final ObjectMapper mapper=new ObjectMapper();
  private String required(JsonNode n,String name){JsonNode v=n.path(name);if(v.isMissingNode()||v.isNull()||v.asText().isBlank())throw new IllegalArgumentException("Missing "+name);return v.asText();}
  public CanonicalPaymentEvent normalize(String provider,byte[] raw,Instant received) {
    try {
      JsonNode j=mapper.readTree(raw);
      if(provider.equals("Quetzal")) {
        JsonNode c=j.path("data").path("charge");String original=required(j,"type");
        String status=switch(original){case "charge.succeeded"->"Captured";case "charge.failed"->"Failed";case "charge.refunded"->"Refunded";default->throw new IllegalArgumentException("Unsupported type");};
        String id=required(j,"id"),pay=required(c,"id"),ref=required(c,"reference");
        if(!"mxn".equals(required(c,"currency")))throw new IllegalArgumentException("Invalid currency");
        BigDecimal value=new BigDecimal(required(c,"amount")).movePointLeft(2);
        if(value.signum()<0)throw new IllegalArgumentException("Negative amount");
        Instant at=Instant.ofEpochSecond(j.path("created").asLong());
        if(status.equals("Captured")&&!c.path("paid_at").isNull()&&c.has("paid_at"))at=Instant.ofEpochSecond(c.path("paid_at").asLong());
        if(status.equals("Refunded")&&!c.path("refunded_at").isNull()&&c.has("refunded_at"))at=Instant.ofEpochSecond(c.path("refunded_at").asLong());
        Map<String,String> metadata= status.equals("Refunded")&&c.has("amount_refunded")?Map.of("refundedAmount",new BigDecimal(required(c,"amount_refunded")).movePointLeft(2).toPlainString()):Map.of();
        if(status.equals("Refunded")&&!c.has("amount_refunded"))throw new IllegalArgumentException("Refund amount required");
        return event("Quetzal","MX",id,pay,"MX:"+pay,"Quetzal:"+id,ref,status,original,value.toPlainString(),"MXN",at,received,metadata);
      }
      if(provider.equals("Andina")) {
        JsonNode t=j.path("transaccion");String original=required(t,"estado");
        String status=switch(original){case "PENDIENTE"->"Pending";case "APROBADA"->"Captured";case "RECHAZADA"->"Failed";case "REVERSADA"->"Refunded";default->throw new IllegalArgumentException("Unsupported state");};
        String id=required(j,"notificacion_id"),pay=required(t,"id"),ref=required(t,"referencia");
        if(!"COP".equals(required(t,"moneda")))throw new IllegalArgumentException("Invalid currency");
        BigDecimal value=new BigDecimal(required(t,"valor"));if(value.signum()<0||value.scale()!=2)throw new IllegalArgumentException("Invalid amount");
        Instant at=OffsetDateTime.parse(required(t,"fecha_transaccion")).toInstant();
        Map<String,String> metadata=t.hasNonNull("motivo_reverso")?Map.of("reasonCode",t.path("motivo_reverso").asText()):Map.of();
        return event("Andina","CO",id,pay,"CO:"+pay,"Andina:"+pay+":"+original,ref,status,original,value.toPlainString(),"COP",at,received,metadata);
      }
      throw new IllegalArgumentException("Unsupported provider");
    } catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Malformed notification",e);}
  }
  private CanonicalPaymentEvent event(String provider,String country,String providerEventId,String paymentId,String key,String idem,String policy,String status,String original,String amount,String currency,Instant occurred,Instant received,Map<String,String> meta){
    return new CanonicalPaymentEvent(UUID.randomUUID(),"1.0","premium.payment.updated",provider,country,providerEventId,paymentId,key,idem,policy,status,original,new CanonicalPaymentEvent.Amount(amount,currency),occurred,received,UUID.randomUUID().toString(),meta);
  }
}
