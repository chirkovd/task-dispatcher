package com.dipegroup.exceptions;

public class TaskDispatcherException extends Exception {

    public TaskDispatcherException(String message) {
        super(message);
    }

    public TaskDispatcherException(String message, Throwable cause) {
        super(message, cause);
    }
}
