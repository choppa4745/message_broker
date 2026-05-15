package com.pigeonmq.sdk.exception;

/**
 * Root exception for all pigeonMQ SDK errors.
 */
public class PigeonException extends RuntimeException {

    public PigeonException(String message) {
        super(message);
    }

    public PigeonException(String message, Throwable cause) {
        super(message, cause);
    }
}
