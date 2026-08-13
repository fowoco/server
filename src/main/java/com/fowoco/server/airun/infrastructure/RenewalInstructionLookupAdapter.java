package com.fowoco.server.airun.infrastructure;

import com.fowoco.server.airun.application.port.AiRunRepository;
import com.fowoco.server.task.application.renewal.RenewalInstructionLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RenewalInstructionLookupAdapter implements RenewalInstructionLookup {

    private final AiRunRepository aiRunRepository;

    public RenewalInstructionLookupAdapter(AiRunRepository aiRunRepository) {
        this.aiRunRepository = aiRunRepository;
    }

    @Override
    public Optional<String> findInstruction(UUID aiRunId, UUID companyId) {
        return aiRunRepository.findByIdAndCompanyId(aiRunId, companyId)
                .map(result -> result.instruction());
    }
}
