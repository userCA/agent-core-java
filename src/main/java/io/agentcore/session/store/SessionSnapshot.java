package io.agentcore.session.store;

import java.util.List;

public record SessionSnapshot(
        SessionHeader header,
        List<SessionEntry> entries
) {}
