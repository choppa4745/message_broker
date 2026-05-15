package com.pigeonmq.sdk.exception;

public class SubscriptionException extends PigeonException {

    public SubscriptionException(String message) {
        super(message);
    }

    public SubscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
