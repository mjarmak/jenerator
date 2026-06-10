package com.jenerator.controlplane.exception;

public class UnauthorizedWorkerException extends RuntimeException {
    public UnauthorizedWorkerException(String message) {
        super(message);
    }
}
