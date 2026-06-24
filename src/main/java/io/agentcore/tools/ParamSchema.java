package io.agentcore.tools;

import java.util.*;

/**
 * Fluent DSL for building JSON Schema parameter definitions.
 *
 * <p>Replaces verbose nested {@code Map.of("type", "object", "properties", ...)}
 * constructions in tool definitions with a readable builder API.
 *
 * <p>Usage:
 * <pre>{@code
 * ParamSchema.object()
 *     .prop("path", ParamSchema.string("File path").required())
 *     .prop("offset", ParamSchema.integer("Line offset").defaultValue(0))
 *     .build()
 * }</pre>
 */
public final class ParamSchema {

    private ParamSchema() {}

    /** Start building an object schema. */
    public static ObjectSchema object() {
        return new ObjectSchema();
    }

    /** Create a string property descriptor. */
    public static Property string(String description) {
        return new Property("string", description);
    }

    /** Create an integer property descriptor. */
    public static Property integer(String description) {
        return new Property("integer", description);
    }

    /** Create a boolean property descriptor. */
    public static Property bool(String description) {
        return new Property("boolean", description);
    }

    /** Create a number property descriptor. */
    public static Property number(String description) {
        return new Property("number", description);
    }

    /** Create an array property descriptor. */
    public static Property array(String description) {
        return new Property("array", description);
    }

    /** Create an object property descriptor. */
    public static Property objectProp(String description) {
        return new Property("object", description);
    }

    /**
     * Property descriptor with type, description, and optional metadata.
     */
    public static final class Property {
        private final String type;
        private final String description;
        private boolean isRequired;
        private Object defaultValue;

        Property(String type, String description) {
            this.type = type;
            this.description = description;
        }

        /** Mark this property as required. */
        public Property required() {
            this.isRequired = true;
            return this;
        }

        /** Set a default value for this property. */
        public Property defaultValue(Object value) {
            this.defaultValue = value;
            return this;
        }

        Map<String, Object> toSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", type);
            schema.put("description", description);
            if (defaultValue != null) {
                schema.put("default", defaultValue);
            }
            return Map.copyOf(schema);
        }

        boolean isRequired() {
            return isRequired;
        }
    }

    /**
     * Builder for object-type schemas with named properties.
     */
    public static final class ObjectSchema {
        private final LinkedHashMap<String, Property> properties = new LinkedHashMap<>();

        /** Add a named property. */
        public ObjectSchema prop(String name, Property property) {
            properties.put(name, property);
            return this;
        }

        /** Build the schema as a {@code Map<String, Object>} for use in ToolDefinition. */
        public Map<String, Object> build() {
            Map<String, Object> props = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();

            for (var entry : properties.entrySet()) {
                props.put(entry.getKey(), entry.getValue().toSchema());
                if (entry.getValue().isRequired()) {
                    required.add(entry.getKey());
                }
            }

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", Map.copyOf(props));
            if (!required.isEmpty()) {
                schema.put("required", List.copyOf(required));
            }
            return Map.copyOf(schema);
        }
    }
}
