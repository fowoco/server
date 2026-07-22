package com.fowoco.server.auth.application.port;

import com.fowoco.server.auth.application.ActorContext;

public interface ActorContextProvider {

    ActorContext requireCurrentActor();
}
