package com.fowoco.server.document.domain;

import com.fowoco.server.worker.domain.DocumentType;
import java.util.Map;
import java.util.Optional;

public final class ChecklistItemDocumentMapper {

    private static final Map<String, DocumentType> ITEM_CODE_TO_DOCUMENT_TYPE = Map.of(
            "PASSPORT_COPY_VERIFY_CURRENT", DocumentType.PASSPORT_COPY,
            "ALIEN_REGISTRATION_CARD_VERIFY_CURRENT", DocumentType.ARC,
            "EMPLOYMENT_CONTRACT_VERIFY_PERIOD", DocumentType.CONTRACT,
            "EMPLOYMENT_CONTRACT_USE_CURRENT_STANDARD_FORM", DocumentType.CONTRACT,
            "EMPLOYMENT_PERMIT_VERIFY_PERIOD", DocumentType.PERMIT
    );

    private ChecklistItemDocumentMapper() {
    }

    public static Optional<DocumentType> toDocumentType(String itemCode) {
        return Optional.ofNullable(ITEM_CODE_TO_DOCUMENT_TYPE.get(itemCode));
    }
}
