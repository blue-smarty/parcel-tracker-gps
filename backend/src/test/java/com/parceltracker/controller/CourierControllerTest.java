package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.service.CourierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourierController.class)
class CourierControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private CourierService service;

    @Test
    void updatesCourierDeliveryStatus() throws Exception {
        Parcel parcel = new Parcel();
        parcel.setId("parcel-1");
        parcel.setStatus(ParcelStatus.PICKED_UP);
        when(service.updateStatus("courier-1", "parcel-1", ParcelStatus.PICKED_UP)).thenReturn(parcel);

        mvc.perform(put("/api/couriers/courier-1/deliveries/parcel-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));
    }

    @Test
    void reportsInvalidTransitionAsBadRequest() throws Exception {
        when(service.updateStatus(eq("courier-1"), eq("parcel-1"), any()))
                .thenThrow(new IllegalStateException("Invalid delivery status transition"));

        mvc.perform(put("/api/couriers/courier-1/deliveries/parcel-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid delivery status transition"));
    }
}
