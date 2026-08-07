package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.application.port.UserAgreementConsentRepository;
import com.fowoco.server.auth.domain.UserAgreementConsent;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
public class JpaUserAgreementConsentRepository implements UserAgreementConsentRepository {

    private final EntityManager entityManager;

    public JpaUserAgreementConsentRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insertAll(List<UserAgreementConsent> consents) {
        Objects.requireNonNull(consents, "consents must not be null");
        consents.forEach(consent -> entityManager.persist(new UserAgreementConsentJpaEntity(consent)));
        entityManager.flush();
    }
}
