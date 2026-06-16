package io.agentcore.core.content;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("text")
public record TextContent(String text) implements Content {
    @Override
    public String type() {
        return "text";
    }
}
