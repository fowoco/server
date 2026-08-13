package com.fowoco.server.task.application.renewal;

import java.util.Optional;
import java.util.UUID;

/**
 * Renewal 재실행에 사용할 최초 HR 발화문을 조회하는 application port입니다.
 */
public interface RenewalInstructionLookup {

    Optional<String> findInstruction(UUID aiRunId, UUID companyId);
}
