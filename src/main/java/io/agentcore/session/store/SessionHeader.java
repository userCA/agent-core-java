package io.agentcore.session.store;

public record SessionHeader(
        String type,
        String version,
        String id,
        String timestamp,
        String cwd
) {
    public SessionHeader(String id, String timestamp, String cwd) {
        this("session", "1", id, timestamp, cwd);
    }
}
