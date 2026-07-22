package com.fowoco.server.company.infrastructure.persistence;

import com.fowoco.server.company.domain.Company;
import com.fowoco.server.company.domain.CompanyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "company")
public class CompanyJpaEntity {

    @Id
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CompanyStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CompanyJpaEntity() {
    }

    private CompanyJpaEntity(
            UUID companyId,
            String name,
            CompanyStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.companyId = companyId;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static CompanyJpaEntity fromDomain(Company company) {
        Objects.requireNonNull(company, "company must not be null");
        return new CompanyJpaEntity(
                company.companyId(),
                company.name(),
                company.status(),
                company.createdAt(),
                company.updatedAt(),
                company.version()
        );
    }

    public Company toDomain() {
        return new Company(companyId, name, status, createdAt, updatedAt, version);
    }

}
