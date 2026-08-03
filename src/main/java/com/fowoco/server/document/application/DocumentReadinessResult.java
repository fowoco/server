package com.fowoco.server.document.application;

import com.fowoco.server.worker.domain.DocumentType;
import java.util.List;

public record DocumentReadinessResult(
        List<DocumentType> required,
        List<DocumentType> available,
        List<DocumentType> missing,
        List<DocumentType> expired,
        boolean completionBlocked
) {
}
