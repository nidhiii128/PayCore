package io.paycore.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Signs webhook bodies with HMAC-SHA256 over {@code "<timestamp>.<body>"}. The header looks like
 * {@code t=1700000000,v1=<hex>}. Including the timestamp lets receivers reject replayed requests.
 */
public final class WebhookSigner {

    public static final String HEADER = "Paycore-Signature";

    private WebhookSigner() {
    }

    public static String header(String secret, long epochSeconds, String payload) {
        return "t=" + epochSeconds + ",v1=" + hmac(secret, epochSeconds + "." + payload);
    }

    /** Receiver-side check, also used by the tests. */
    public static boolean verify(String secret, String header, String payload) {
        if (header == null) {
            return false;
        }
        String timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            switch (kv[0].trim()) {
                case "t" -> timestamp = kv[1].trim();
                case "v1" -> signature = kv[1].trim();
                default -> {
                }
            }
        }
        if (timestamp == null || signature == null) {
            return false;
        }
        String expected = hmac(secret, timestamp + "." + payload);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hmac(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
