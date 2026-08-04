package com.fowoco.server.airun.api;

import com.fowoco.server.airun.application.AiRunQuestionResult;

public record AiRunQuestionResponse(
        String slotKey,
        String label,
        String inputType,
        boolean required,
        String answer
) {
    static AiRunQuestionResponse from(AiRunQuestionResult result) {
        return new AiRunQuestionResponse(
                result.slotKey(),
                result.label(),
                result.inputType(),
                result.required(),
                result.answer()
        );
    }
}
