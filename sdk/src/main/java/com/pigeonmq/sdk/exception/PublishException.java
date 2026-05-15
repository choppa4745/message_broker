package com.pigeonmq.sdk.exception;

public class PublishException extends PigeonException {

    public PublishException(String message) {
        super(message);
    }

    public PublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
