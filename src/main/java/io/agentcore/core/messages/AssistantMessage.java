package io.agentcore.core.messages;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.agentcore.core.content.Content;
import io.agentcore.core.content.ToolCallContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonTypeName("assistant")
public record AssistantMessage(
        List<Content> content,
        Usage usage,
        StopReason stopReason,
        String errorMessage,
        boolean retryableError,
        boolean overflowError,
        String provider,
        String model,
        double timestamp
) implements AgentMessage {

    public AssistantMessage {
        if (content == null) content = List.of();
    }

    /**
     * Convenience constructor for building a fresh assistant message.
     */
    public AssistantMessage() {
        this(List.of(), new Usage(), null, null, false, false, null, null,
                System.currentTimeMillis() / 1000.0);
    }

    @Override
    public String role() {
        return "assistant";
    }

    public List<ToolCallContent> toolCalls() {
        return content.stream()
                .filter(c -> c instanceof ToolCallContent)
                .map(c -> (ToolCallContent) c)
                .toList();
    }

    public boolean hasToolCalls() {
        return content.stream().anyMatch(c -> c instanceof ToolCallContent);
    }

    /**
     * Create a mutable copy with updated fields. Records are immutable so we need
     * builder-style methods for mutation during streaming accumulation.
     */
    public MutableAssistant toMutable() {
        return new MutableAssistant(this);
    }

    /**
     * Mutable wrapper for streaming accumulation.
     */
    public static final class MutableAssistant {
        private List<Content> content;
        private Usage usage;
        private StopReason stopReason;
        private String errorMessage;
        private boolean retryableError;
        private boolean overflowError;
        private String provider;
        private String model;
        private final double timestamp;

        public MutableAssistant(AssistantMessage src) {
            this.content = new ArrayList<>(src.content());
            this.usage = src.usage();
            this.stopReason = src.stopReason();
            this.errorMessage = src.errorMessage();
            this.retryableError = src.retryableError();
            this.overflowError = src.overflowError();
            this.provider = src.provider();
            this.model = src.model();
            this.timestamp = src.timestamp();
        }

        public List<Content> content() { return Collections.unmodifiableList(content); }
        public void content(List<Content> c) { this.content = c; }
        public Usage usage() { return usage; }
        public void usage(Usage u) { this.usage = u; }
        public StopReason stopReason() { return stopReason; }
        public void stopReason(StopReason s) { this.stopReason = s; }
        public String errorMessage() { return errorMessage; }
        public void errorMessage(String e) { this.errorMessage = e; }
        public boolean retryableError() { return retryableError; }
        public void retryableError(boolean r) { this.retryableError = r; }
        public boolean overflowError() { return overflowError; }
        public void overflowError(boolean o) { this.overflowError = o; }
        public String provider() { return provider; }
        public void provider(String p) { this.provider = p; }
        public String model() { return model; }
        public void model(String m) { this.model = m; }
        public double timestamp() { return timestamp; }

        public AssistantMessage toRecord() {
            return new AssistantMessage(
                    new ArrayList<>(content), usage, stopReason, errorMessage,
                    retryableError, overflowError, provider, model, timestamp
            );
        }

        public boolean hasToolCalls() {
            return content.stream().anyMatch(c -> c instanceof ToolCallContent);
        }

        public List<ToolCallContent> toolCalls() {
            return content.stream()
                    .filter(c -> c instanceof ToolCallContent)
                    .map(c -> (ToolCallContent) c)
                    .toList();
        }
    }
}
