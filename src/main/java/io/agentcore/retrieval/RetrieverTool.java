package io.agentcore.retrieval;

import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;
import io.agentcore.tools.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * Retrieval tool that wraps a {@link Retriever} as an LLM-callable tool.
 *
 * <p>When the model calls this tool with a natural-language query, the
 * retriever searches its index and returns formatted results including
 * relevance scores and source tags.
 */
public class RetrieverTool implements Tool {

    private static final String DEFAULT_NAME = "retrieve";
    private static final String DEFAULT_DESCRIPTION =
            "Retrieve relevant context documents for a natural-language query. "
                    + "Returns ranked chunks with relevance scores.";

    private final Retriever retriever;
    private final String toolName;
    private final String toolDescription;

    public RetrieverTool(Retriever retriever) {
        this(retriever, DEFAULT_NAME, DEFAULT_DESCRIPTION);
    }

    public RetrieverTool(Retriever retriever, String name, String description) {
        this.retriever = retriever;
        this.toolName = name;
        this.toolDescription = description;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                toolName,
                toolDescription,
                Map.of("type", "object", "properties", Map.of(
                        "query", Map.of("type", "string",
                                "description", "Natural-language search query"),
                        "top_k", Map.of("type", "integer",
                                "description", "Maximum number of results to return",
                                "default", 5),
                        "filters", Map.of("type", "object",
                                "description", "Optional metadata equality filters (key-value pairs)")
                ), "required", List.of("query")),
                null, null, null
        );
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        String queryText = (String) params.get("query");
        if (queryText == null || queryText.isBlank()) {
            return new ToolResult("ERROR: 'query' parameter is required");
        }

        Object topKObj = params.get("top_k");
        int topK = topKObj instanceof Number n ? n.intValue() : 5;

        @SuppressWarnings("unchecked")
        Map<String, Object> filters = params.get("filters") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        Query query = new Query(queryText, topK, filters);

        try {
            List<RetrievedChunk> chunks = retriever.retrieve(query).join();
            if (chunks == null || chunks.isEmpty()) {
                return new ToolResult("No matching results.");
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < chunks.size(); i++) {
                RetrievedChunk c = chunks.get(i);
                if (i > 0) sb.append("\n\n");
                sb.append('[').append(i + 1).append("] ");
                sb.append("(score=").append(String.format("%.3f", c.score())).append(')');
                if (c.source() != null) {
                    sb.append(" [").append(c.source()).append(']');
                }
                sb.append('\n').append(c.text());
            }
            return new ToolResult(sb.toString());
        } catch (Exception e) {
            return new ToolResult("Error retrieving: " + e.getMessage());
        }
    }
}
