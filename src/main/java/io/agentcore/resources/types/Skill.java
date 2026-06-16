package io.agentcore.resources.types;

public record Skill(String name, String description, String content,
                     SourceInfo source, boolean disableModelInvocation) {}
