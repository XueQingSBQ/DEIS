package org.bdware.drirp.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class DrirpAuthUtils {
    private DrirpAuthUtils() {
    }

    public static String hmacSha256Base64(byte[] secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        byte[] signed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signed);
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}


