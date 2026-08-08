package com.fowoco.server.workerimport.application.port;

import com.fowoco.server.workerimport.application.ParsedWorkerImport;

public interface WorkerImportFileParser {
    ParsedWorkerImport parse(String fileName, byte[] content);
}
