package com.travel.global.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BedrockJsonTest {
    @Test
    void extractsJsonObjectFromMarkdownResponse() {
        assertThat(BedrockJson.extractObject("```json\n{\"id\":1}\n```"))
                .isEqualTo("{\"id\":1}");
    }

    @Test
    void rejectsMissingOrInvalidJson() {
        assertThatThrownBy(() -> BedrockJson.extractObject(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BedrockJson.extractObject("   "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BedrockJson.extractObject("plain text"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BedrockJson.extractObject("} invalid {"))
                .isInstanceOf(IllegalStateException.class);
    }
}
