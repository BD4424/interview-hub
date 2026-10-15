package com.prephub.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {
    @Test void caseAndWhitespaceProduceSameFingerprint() {
        assertThat(TextNormalizer.fingerprint("  Explain   HashMap\tinternals? "))
                .isEqualTo(TextNormalizer.fingerprint("explain hashmap internals"));
    }
    @Test void differentQuestionsDiffer() {
        assertThat(TextNormalizer.fingerprint("Explain HashMap internals"))
                .isNotEqualTo(TextNormalizer.fingerprint("Explain ConcurrentHashMap internals"));
    }
    @Test void blankIsNull() {
        assertThat(TextNormalizer.fingerprint("   ")).isNull();
        assertThat(TextNormalizer.fingerprint("???")).isNull();
    }
    @Test void companyNamesNormalize() {
        assertThat(TextNormalizer.normalizeName("  AMAZON  ")).isEqualTo("amazon");
        assertThat(TextNormalizer.normalizeName("LTI\u00A0 Mindtree")).isEqualTo("lti mindtree");
    }
    @Test void fullWidthCharactersNormalize() {
        assertThat(TextNormalizer.normalizeName("ＴＣＳ")).isEqualTo("tcs");
    }
    @Test void slug() { assertThat(TextNormalizer.slugify("Spring Boot")).isEqualTo("spring-boot"); }
}
