package com.fowoco.server.worker.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;

class WorkerDisplayNameNormalizerTest {

    private final WorkerDisplayNameNormalizer normalizer = new WorkerDisplayNameNormalizer();

    @Test
    void normalizesUnicodeSpacingSeparatorsAndCaseWithoutGuessingPronunciation() {
        String decomposed = Normalizer.normalize("응우옌 반 안", Normalizer.Form.NFD);

        assertThat(normalizer.normalize(decomposed)).isEqualTo("응우옌반안");
        assertThat(normalizer.normalize("응 우 옌-반_안")).isEqualTo("응우옌반안");
        assertThat(normalizer.normalize("NGUYEN-VAN_AN")).isEqualTo("nguyenvanan");
        assertThat(normalizer.normalize("누엔 반 안")).isNotEqualTo("응우옌반안");
    }

    @Test
    void rejectsBlankOrSeparatorOnlyNames() {
        assertThatThrownBy(() -> normalizer.normalize("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> normalizer.normalize("- _ ."))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
