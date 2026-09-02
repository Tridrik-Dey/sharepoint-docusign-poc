package com.example.sharepointdocusign.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignatureVerifierTest {

    private static final byte[] BODY = "{\"event\":\"envelope-completed\",\"data\":{\"envelopeId\":\"env-1\"}}"
            .getBytes(StandardCharsets.UTF_8);
    private static final String SECRET = "test-webhook-secret";

    @Test
    void acceptsACorrectlyComputedSignature() {
        String signature = independentlyComputeSignature(BODY, SECRET);

        assertThat(HmacSignatureVerifier.isValid(BODY, SECRET, signature)).isTrue();
    }

    @Test
    void rejectsATamperedBody() {
        String signature = independentlyComputeSignature(BODY, SECRET);
        byte[] tampered = "{\"event\":\"envelope-completed\",\"data\":{\"envelopeId\":\"env-2\"}}"
                .getBytes(StandardCharsets.UTF_8);

        assertThat(HmacSignatureVerifier.isValid(tampered, SECRET, signature)).isFalse();
    }

    @Test
    void rejectsTheWrongSecret() {
        String signature = independentlyComputeSignature(BODY, SECRET);

        assertThat(HmacSignatureVerifier.isValid(BODY, "wrong-secret", signature)).isFalse();
    }

    @Test
    void rejectsWhenSecretIsBlank() {
        String signature = independentlyComputeSignature(BODY, SECRET);

        assertThat(HmacSignatureVerifier.isValid(BODY, "", signature)).isFalse();
        assertThat(HmacSignatureVerifier.isValid(BODY, null, signature)).isFalse();
    }

    @Test
    void rejectsWhenSignatureHeaderIsMissing() {
        assertThat(HmacSignatureVerifier.isValid(BODY, SECRET, "")).isFalse();
        assertThat(HmacSignatureVerifier.isValid(BODY, SECRET, null)).isFalse();
    }

    /** Computed independently of HmacSignatureVerifier's own implementation, so a bug there can't cancel itself out. */
    private String independentlyComputeSignature(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(body));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
