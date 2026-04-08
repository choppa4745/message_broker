package com.pigeonmq.exception;

public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceName;

    public ResourceNotFoundException(String resourceType, String resourceName) {
        super(resourceType + " not found: " + resourceName);
        this.resourceType = resourceType;
        this.resourceName = resourceName;
    }

    public String getResourceType() { return resourceType; }
    public String getResourceName() { return resourceName; }
}
