package com.pigeonmq.sdk.exception;

public class ConnectionException extends PigeonException {

    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
