package com.fowoco.server.airun.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DecideAiRunCandidatesRequest(
        @Min(0) long expectedRunVersion,
        @NotNull @NotEmpty @Size(max = 20) List<@Valid AiCandidateDecisionItemRequest> decisions
) {
    public DecideAiRunCandidatesRequest {
        decisions = decisions == null ? null : List.copyOf(decisions);
    }
}
