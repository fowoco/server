package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.file.application.DocumentPreviewConversionException;
import com.fowoco.server.file.application.DocumentPreviewSource;
import com.fowoco.server.file.application.port.DocumentPreviewConverter;

final class DisabledDocumentPreviewConverter implements DocumentPreviewConverter {

    @Override
    public byte[] convertToPdf(DocumentPreviewSource source) {
        throw new DocumentPreviewConversionException(
                DocumentPreviewConversionException.Reason.UNAVAILABLE,
                "Document preview conversion is disabled."
        );
    }
}
