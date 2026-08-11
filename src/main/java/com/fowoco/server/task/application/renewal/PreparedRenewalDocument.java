package com.fowoco.server.task.application.renewal;

import com.fowoco.server.aiintegration.application.document.GeneratedDocumentFile;
import com.fowoco.server.aiintegration.application.renewal.RenewalGeneratedDocument;

record PreparedRenewalDocument(
        RenewalGeneratedDocument descriptor,
        GeneratedDocumentFile file
) {
}
