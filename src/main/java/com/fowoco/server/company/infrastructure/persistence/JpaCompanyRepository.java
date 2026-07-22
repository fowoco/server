package com.fowoco.server.company.infrastructure.persistence;

import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.domain.Company;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCompanyRepository implements CompanyRepository {

    private final EntityManager entityManager;

    public JpaCompanyRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Company> findById(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return Optional.ofNullable(entityManager.find(CompanyJpaEntity.class, companyId))
                .map(CompanyJpaEntity::toDomain);
    }

    @Override
    public void insert(Company company) {
        Objects.requireNonNull(company, "company must not be null");
        entityManager.persist(CompanyJpaEntity.fromDomain(company));
        entityManager.flush();
    }
}
