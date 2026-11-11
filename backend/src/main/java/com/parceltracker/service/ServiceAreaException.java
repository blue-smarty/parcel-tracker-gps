package com.parceltracker.service;

import org.springframework.http.HttpStatus;

public class ServiceAreaException extends RuntimeException {
    private final HttpStatus status;

    public ServiceAreaException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
