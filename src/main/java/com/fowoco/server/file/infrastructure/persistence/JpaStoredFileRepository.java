package com.fowoco.server.file.infrastructure.persistence;

import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaStoredFileRepository implements StoredFileRepository {

    private final EntityManager entityManager;

    public JpaStoredFileRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insert(StoredFile storedFile) {
        Objects.requireNonNull(storedFile, "storedFile must not be null");
        entityManager.persist(StoredFileJpaEntity.fromDomain(storedFile));
        entityManager.flush();
    }

    @Override
    public Optional<StoredFile> findByIdAndCompanyId(UUID storedFileId, UUID companyId) {
        Objects.requireNonNull(storedFileId, "storedFileId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        return entityManager.createQuery(
                        """
                        select storedFile
                        from StoredFileJpaEntity storedFile
                        where storedFile.storedFileId = :storedFileId
                          and storedFile.companyId = :companyId
                        """,
                        StoredFileJpaEntity.class
                )
                .setParameter("storedFileId", storedFileId)
                .setParameter("companyId", companyId)
                .getResultStream()
                .findFirst()
                .map(StoredFileJpaEntity::toDomain);
    }
}
