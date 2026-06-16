package io.agentcore.core.messages;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.agentcore.core.content.Content;

import java.util.List;

@JsonTypeName("user")
public record UserMessage(
        List<Content> content,
        double timestamp
) implements AgentMessage {

    public UserMessage {
        if (content == null) content = List.of();
    }

    public UserMessage(List<Content> content) {
        this(content, System.currentTimeMillis() / 1000.0);
    }

    @Override
    public String role() {
        return "user";
    }
}
