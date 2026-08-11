package com.fowoco.server.workerlink.infrastructure.sms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.workerlink.application.port.WorkerLinkSmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.worker-link.sms.provider=solapi",
        "app.worker-link.sms.api-key=test-api-key",
        "app.worker-link.sms.api-secret=test-api-secret",
        "app.worker-link.sms.sender-number=029999999"
})
class WorkerLinkSolapiConfigurationTest {

    @Autowired
    private WorkerLinkSmsSender sender;

    @Test
    void solapiProviderSelectsRealAdapter() {
        assertThat(sender).isInstanceOf(SolapiWorkerLinkSmsSender.class);
    }
}
