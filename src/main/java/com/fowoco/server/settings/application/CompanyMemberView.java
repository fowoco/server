package com.fowoco.server.settings.application;

import java.util.UUID;

public sealed interface CompanyMemberView
        permits DetailedCompanyMemberView, MinimalCompanyMemberView {

    UUID userId();

    String displayName();
}
