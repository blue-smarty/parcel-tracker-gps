package com.parceltracker.controller;

import com.parceltracker.service.ServiceAreaException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ServiceAreaErrorHandler {
    @ExceptionHandler(ServiceAreaException.class)
    public ResponseEntity<Map<String, String>> handle(ServiceAreaException error) {
        return ResponseEntity.status(error.getStatus()).body(Map.of("error", error.getMessage()));
    }
}
