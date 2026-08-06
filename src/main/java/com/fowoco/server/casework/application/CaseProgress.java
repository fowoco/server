package com.fowoco.server.casework.application;

public record CaseProgress(int completedSteps, int totalSteps, int percentage) {
}
