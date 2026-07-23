package com.fowoco.server.approval.application;

public record DecideApprovalCommand(long expectedVersion, String reason) {
}
