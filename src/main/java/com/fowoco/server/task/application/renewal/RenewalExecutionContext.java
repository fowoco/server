package com.fowoco.server.task.application.renewal;

import com.fowoco.server.aiintegration.application.renewal.RenewalCompanySnapshot;
import com.fowoco.server.aiintegration.application.renewal.RenewalDocumentInput;
import com.fowoco.server.aiintegration.application.renewal.RenewalTaskSnapshot;
import com.fowoco.server.aiintegration.application.renewal.RenewalWorkerSnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record RenewalExecutionContext(
        UUID taskId,
        UUID companyId,
        UUID workerId,
        Map<String, Object> slots,
        Map<String, String> submittedSlotAnswers,
        List<RenewalDocumentInput> documents,
        Map<String, Object> ocrResult,
        RenewalWorkerSnapshot worker,
        RenewalCompanySnapshot company,
        RenewalTaskSnapshot task
) {
}
