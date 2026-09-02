package com.example.sharepointdocusign.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies the HMAC-SHA256 signature DocuSign Connect sends on the
 * X-DocuSign-Signature-1 header, computed over the exact raw request body.
 * Fails closed: a missing/blank secret or header is always treated as
 * invalid, never as "no verification needed" - this guards a public
 * endpoint that triggers a real SharePoint write.
 */
public final class HmacSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSignatureVerifier() {
    }

    public static boolean isValid(byte[] rawBody, String secret, String signatureHeaderValue) {
        if (secret == null || secret.isBlank() || signatureHeaderValue == null || signatureHeaderValue.isBlank()) {
            return false;
        }
        String expected = computeSignature(rawBody, secret);
        // Constant-time comparison on the encoded strings' bytes, to avoid a timing side-channel.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeaderValue.getBytes(StandardCharsets.UTF_8));
    }

    private static String computeSignature(byte[] rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(rawBody));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is not available on this JVM", e);
        }
    }
}
