package com.fowoco.server.workerlink.application.port;

public interface WorkerLinkSmsSender {

    void send(WorkerLinkSmsMessage message);
}
