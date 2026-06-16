package io.agentcore.core.content;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonTypeName("image")
public record ImageContent(
        String data,
        @JsonProperty("mime_type") String mimeType
) implements Content {
    @Override
    public String type() {
        return "image";
    }
}
