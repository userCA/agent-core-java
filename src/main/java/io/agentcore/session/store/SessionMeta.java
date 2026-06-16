package io.agentcore.session.store;

public record SessionMeta(
        String sessionId,
        String createdAt,
        int entryCount,
        String title
) {}
