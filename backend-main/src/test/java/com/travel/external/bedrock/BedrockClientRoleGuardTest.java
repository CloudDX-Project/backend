package com.travel.external.bedrock;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BedrockClientRoleGuardTest {

    @Test
    void apiRuntimeCannotInvokeBedrock() {
        BedrockClient client = new BedrockClient(mock(BedrockRuntimeClient.class));
        ReflectionTestUtils.setField(client, "runtimeRole", "api");
        ReflectionTestUtils.setField(client, "modelId", "apac.amazon.nova-lite-v1:0");

        assertThatThrownBy(() -> client.converse("prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("worker");
    }
}
