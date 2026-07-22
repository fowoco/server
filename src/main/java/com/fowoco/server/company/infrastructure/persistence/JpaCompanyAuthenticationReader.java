package com.fowoco.server.company.infrastructure.persistence;

import com.fowoco.server.company.application.CompanyAuthenticationReader;
import com.fowoco.server.company.application.CompanyAuthenticationSnapshot;
import com.fowoco.server.company.domain.Company;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCompanyAuthenticationReader implements CompanyAuthenticationReader {

    private final EntityManager entityManager;

    public JpaCompanyAuthenticationReader(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<CompanyAuthenticationSnapshot> findByCompanyId(UUID companyId) {
        return Optional.ofNullable(entityManager.find(CompanyJpaEntity.class, companyId))
                .map(CompanyJpaEntity::toDomain)
                .map(this::toSnapshot);
    }

    private CompanyAuthenticationSnapshot toSnapshot(Company company) {
        return new CompanyAuthenticationSnapshot(
                company.companyId(),
                company.name(),
                company.isActive()
        );
    }
}
