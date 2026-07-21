package com.fowoco.server.common.id;

import java.util.UUID;

@FunctionalInterface
public interface UuidGenerator {

    UUID generate();
}
