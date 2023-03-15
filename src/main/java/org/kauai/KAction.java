package org.kauai;

public class KAction {
    public enum Type { LOGS, SHELL, DESCRIBE }
    private final String resourceType;
    private final String resourceName;
    private final Type type;

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceName() {
        return resourceName;
    }

    public Type getType() {
        return type;
    }

    public String getDescription() {
        return String.format("%s %s %s", resourceName, resourceType, type.name().toLowerCase());
    }

    public KAction(String resourceType, String resourceName, Type type) {
        this.resourceType = resourceType;
        this.resourceName = resourceName;
        this.type = type;
    }
}
