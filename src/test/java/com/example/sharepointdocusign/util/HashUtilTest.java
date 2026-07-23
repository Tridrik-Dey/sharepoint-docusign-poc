package com.example.sharepointdocusign.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilTest {

    @Test
    void sha256ProducesKnownDigestForKnownInput() {
        byte[] input = "hello world".getBytes(StandardCharsets.UTF_8);

        String hash = HashUtil.sha256(input);

        assertThat(hash).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }

    @Test
    void sha256IsDeterministicAndSensitiveToContent() {
        byte[] a = "content-a".getBytes(StandardCharsets.UTF_8);
        byte[] b = "content-b".getBytes(StandardCharsets.UTF_8);

        assertThat(HashUtil.sha256(a)).isEqualTo(HashUtil.sha256(a));
        assertThat(HashUtil.sha256(a)).isNotEqualTo(HashUtil.sha256(b));
    }
}
