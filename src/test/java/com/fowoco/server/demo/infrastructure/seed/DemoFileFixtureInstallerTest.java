package com.fowoco.server.demo.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.StoredFileSeed;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

class DemoFileFixtureInstallerTest {

    @TempDir
    Path storageRoot;

    @Test
    void installsReusesAndRejectsConflictingFixtureContent() throws Exception {
        UUID fileId = UUID.fromString("94800000-0000-0000-0000-000000000001");
        StoredFileSeed seed = new StoredFileSeed(
                fileId,
                "demo-contract-renewal.pdf",
                "application/pdf",
                "DEMO_CONTRACT_RENEWAL",
                UUID.randomUUID(),
                UUID.randomUUID(),
                fileId.toString(),
                "demo/files/demo-contract-renewal.pdf"
        );
        DemoFileFixtureInstaller installer = new DemoFileFixtureInstaller(storageRoot.toString());
        byte[] expected;
        try (var input = new ClassPathResource(seed.resourcePath()).getInputStream()) {
            expected = input.readAllBytes();
        }

        installer.install(seed);
        installer.install(seed);

        Path installed = storageRoot.resolve(seed.storageKey());
        assertThat(Files.readAllBytes(installed)).isEqualTo(expected);

        Files.writeString(installed, "conflicting fixture");
        assertThatThrownBy(() -> installer.install(seed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(seed.storageKey());
    }
}
