package io.agentcore.resources.types;

import java.util.List;

public record PromptTemplate(String name, String description, String template,
                              List<String> parameters, SourceInfo source) {}
