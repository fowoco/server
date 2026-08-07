package com.fowoco.server.document.application;

import java.util.Map;

record DocumentOcrCorrectedFieldsPayload(Map<String, String> fields) {

    DocumentOcrCorrectedFieldsPayload {
        fields = Map.copyOf(fields);
    }
}
