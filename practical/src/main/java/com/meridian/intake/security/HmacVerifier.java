package com.meridian.intake.security;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
public final class HmacVerifier {
  private final Clock clock;
  public HmacVerifier(Clock clock) { this.clock=clock; }
  private byte[] hmac(byte[] bytes, String secret) {
    try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return mac.doFinal(bytes); }
    catch(Exception e) { throw new IllegalStateException("HMAC unavailable",e); }
  }
  private static boolean equals(byte[] expected, byte[] supplied) {return MessageDigest.isEqual(expected,supplied);}
  public boolean quetzal(byte[] body,String header,String secret) {
    if(header==null) return false;
    String ts=null; java.util.List<String> signatures=new java.util.ArrayList<>();
    for(String part:header.split(",")) {String s=part.trim();if(s.startsWith("t="))ts=s.substring(2);else if(s.startsWith("v1="))signatures.add(s.substring(3));}
    try {
      long epoch=Long.parseLong(ts);
      if(Math.abs(Duration.between(Instant.ofEpochSecond(epoch),clock.instant()).toSeconds())>300)return false;
      byte[] prefix=(ts+".").getBytes(StandardCharsets.UTF_8);
      byte[] signed=new byte[prefix.length+body.length];System.arraycopy(prefix,0,signed,0,prefix.length);System.arraycopy(body,0,signed,prefix.length,body.length);
      byte[] expected=hmac(signed,secret);
      for(String sig:signatures) if(equals(expected,HexFormat.of().parseHex(sig)))return true;
    }catch(RuntimeException e){return false;}return false;
  }
  public boolean andina(byte[] body,String timestamp,String signature,String secret) {
    if(timestamp==null||signature==null)return false;
    try {
      Instant at=OffsetDateTime.parse(timestamp).toInstant();
      if(Math.abs(Duration.between(at,clock.instant()).toSeconds())>600)return false;
      byte[] prefix=(timestamp+".").getBytes(StandardCharsets.UTF_8);
      byte[] signed=new byte[prefix.length+body.length];System.arraycopy(prefix,0,signed,0,prefix.length);System.arraycopy(body,0,signed,prefix.length,body.length);
      return equals(hmac(signed,secret),Base64.getDecoder().decode(signature));
    }catch(RuntimeException e){return false;}
  }
}
