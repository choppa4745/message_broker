package com.pigeonmq.sdk.exception;

public class AdminException extends PigeonException {

    private final int httpStatus;

    public AdminException(String message) {
        this(message, -1, null);
    }

    public AdminException(String message, int httpStatus) {
        this(message, httpStatus, null);
    }

    public AdminException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
