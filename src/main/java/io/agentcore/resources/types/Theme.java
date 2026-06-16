package io.agentcore.resources.types;

import java.util.Map;

public record Theme(String name, Map<String, Object> definition, SourceInfo source) {}
