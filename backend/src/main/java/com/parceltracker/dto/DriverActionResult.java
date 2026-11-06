package com.parceltracker.dto;

import com.parceltracker.model.DriverActionType;
import java.time.Instant;

public record DriverActionResult(DriverActionType type, String message, String proxyNumber, Instant at) {
}
