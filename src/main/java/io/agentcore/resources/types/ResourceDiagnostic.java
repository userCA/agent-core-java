package io.agentcore.resources.types;

public record ResourceDiagnostic(String type, String message, String sourcePath,
                                  String winnerPath, String loserPath) {}
