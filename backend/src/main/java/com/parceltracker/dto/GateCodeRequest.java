package com.parceltracker.dto;

import jakarta.validation.constraints.Size;

public class GateCodeRequest {
    @Size(max = 32)
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
