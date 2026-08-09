package com.fowoco.server.settings.infrastructure.persistence;

import com.fowoco.server.settings.application.port.CompanySettingsRepository;
import com.fowoco.server.settings.domain.CompanySettings;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCompanySettingsRepository implements CompanySettingsRepository {

    private final EntityManager entityManager;
    private final CompanySettingsJsonCodec jsonCodec;

    public JpaCompanySettingsRepository(
            EntityManager entityManager,
            CompanySettingsJsonCodec jsonCodec
    ) {
        this.entityManager = entityManager;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public Optional<CompanySettings> findByCompanyId(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return Optional.ofNullable(entityManager.find(CompanySettingsJpaEntity.class, companyId))
                .map(entity -> entity.toDomain(jsonCodec));
    }

    @Override
    public void insert(CompanySettings companySettings) {
        Objects.requireNonNull(companySettings, "companySettings must not be null");
        entityManager.persist(CompanySettingsJpaEntity.fromDomain(companySettings, jsonCodec));
        entityManager.flush();
    }

    @Override
    public CompanySettings update(CompanySettings companySettings) {
        Objects.requireNonNull(companySettings, "companySettings must not be null");
        CompanySettingsJpaEntity merged = entityManager.merge(
                CompanySettingsJpaEntity.fromDomain(companySettings, jsonCodec)
        );
        entityManager.flush();
        return merged.toDomain(jsonCodec);
    }
}
