package com.fowoco.server.workerlink.application;

import com.fowoco.server.worker.domain.DocumentType;

public record WorkerRequestedAction(
        WorkerRequestedActionType type,
        String fieldKey,
        String label,
        WorkerRequestedActionInputType inputType,
        boolean required,
        DocumentType documentType
) {

    public static WorkerRequestedAction answer(
            String fieldKey,
            String label,
            WorkerRequestedActionInputType inputType
    ) {
        return new WorkerRequestedAction(
                WorkerRequestedActionType.ANSWER_FIELD,
                fieldKey,
                label,
                inputType,
                true,
                null
        );
    }

    public static WorkerRequestedAction upload(DocumentType documentType) {
        return new WorkerRequestedAction(
                WorkerRequestedActionType.UPLOAD_DOCUMENT,
                null,
                documentLabel(documentType) + " 파일을 제출해 주세요.",
                null,
                true,
                documentType
        );
    }

    private static String documentLabel(DocumentType documentType) {
        return switch (documentType) {
            case PASSPORT_COPY -> "여권 사본";
            case ARC -> "외국인등록증";
            case CONTRACT -> "근로계약서";
            case PERMIT -> "고용허가 관련 서류";
        };
    }
}
