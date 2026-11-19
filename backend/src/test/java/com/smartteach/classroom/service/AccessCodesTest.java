package com.smartteach.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccessCodesTest {

    @Test
    void generatedCodesUseOnlyTheUnambiguousAlphabet() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String code = AccessCodes.generate();
            assertThat(code).hasSize(AccessCodes.LENGTH).doesNotContainPattern("[01ILO]");
            assertThat(code.chars()).allMatch(c -> AccessCodes.ALPHABET.indexOf(c) >= 0);
            codes.add(code);
        }
        assertThat(codes).hasSize(1000);
    }

    @Test
    void hashIgnoresCaseSpacesAndDashes() {
        String code = "ABCDE23456";
        assertThat(AccessCodes.hash(" abcde-23456 ")).isEqualTo(AccessCodes.hash(code));
        assertThat(AccessCodes.hash(code)).hasSize(64).isNotEqualTo(code);
        assertThat(AccessCodes.normalize(null)).isEmpty();
    }

    @Test
    void hintIsTheLastFourCharacters() {
        assertThat(AccessCodes.hint("ABCDE23456")).isEqualTo("3456");
    }
}
