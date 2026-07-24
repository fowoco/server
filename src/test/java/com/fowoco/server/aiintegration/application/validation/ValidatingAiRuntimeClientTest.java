package com.fowoco.server.aiintegration.application.validation;

import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.requestWithInstruction;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validRequest;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.support.FakeAiRuntimeClient;
import org.junit.jupiter.api.Test;

class ValidatingAiRuntimeClientTest {

    private final AiRuntimeContractValidator validator =
            new AiRuntimeContractValidator(new AiRuntimePrivacyPolicy());

    @Test
    void validatesBothSidesAndCapturesOneAttemptWithoutTransparentRetry() {
        FakeAiRuntimeClient fake = new FakeAiRuntimeClient();
        fake.enqueueResponse(validResponse());
        ValidatingAiRuntimeClient client = new ValidatingAiRuntimeClient(fake, validator);

        assertThat(client.analyze(validRequest())).isEqualTo(validResponse());
        assertThat(fake.receivedRequests()).containsExactly(validRequest());
    }

    @Test
    void rejectedInputNeverReachesTransport() {
        FakeAiRuntimeClient fake = new FakeAiRuntimeClient();
        fake.enqueueResponse(validResponse());
        ValidatingAiRuntimeClient client = new ValidatingAiRuntimeClient(fake, validator);

        assertThatThrownBy(() -> client.analyze(requestWithInstruction("전화 010-1234-5678")))
                .isInstanceOf(AiRuntimeContractException.class);
        assertThat(fake.receivedRequests()).isEmpty();
    }
}
