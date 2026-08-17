package com.fowoco.server.file.application.port;

import com.fowoco.server.file.application.DocumentPreviewSource;

public interface DocumentPreviewConverter {

    byte[] convertToPdf(DocumentPreviewSource source);
}
