package com.parceltracker.service;

import java.io.IOException;

public class ApiException extends IOException {
    private final int statusCode;
    private final String body;

    public ApiException(int statusCode, String body) {
        super("HTTP " + statusCode + ": " + body);
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() { return statusCode; }
    public String getBody() { return body; }
}
