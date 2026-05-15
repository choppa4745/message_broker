package com.pigeonmq.dto;

public record MutationResult(
        boolean success,
        String name,
        String message
) {

    public static MutationResult created(String name) {
        return new MutationResult(true, name, "Created successfully");
    }

    public static MutationResult deleted(String name) {
        return new MutationResult(true, name, "Deleted successfully");
    }
}
