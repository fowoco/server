package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.application.CompanyMemberAccount;
import com.fowoco.server.auth.application.port.CompanyMemberDirectory;
import com.fowoco.server.auth.domain.AccountStatus;
import com.fowoco.server.auth.domain.UserRole;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCompanyMemberDirectory implements CompanyMemberDirectory {

    private final EntityManager entityManager;

    public JpaCompanyMemberDirectory(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<CompanyMemberAccount> findByCompanyId(
            UUID companyId,
            UserRole role,
            boolean activeOnly
    ) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        StringBuilder jpql = new StringBuilder("""
                select new com.fowoco.server.auth.application.CompanyMemberAccount(
                    account.userId,
                    account.displayName,
                    account.role,
                    account.status
                )
                from UserAccountJpaEntity account
                where account.companyId = :companyId
                """);
        if (role != null) {
            jpql.append(" and account.role = :role");
        }
        if (activeOnly) {
            jpql.append(" and account.status = :activeStatus");
        }
        jpql.append(" order by account.displayName asc, account.userId asc");

        var query = entityManager.createQuery(jpql.toString(), CompanyMemberAccount.class)
                .setParameter("companyId", companyId);
        if (role != null) {
            query.setParameter("role", role);
        }
        if (activeOnly) {
            query.setParameter("activeStatus", AccountStatus.ACTIVE);
        }
        return List.copyOf(query.getResultList());
    }
}
