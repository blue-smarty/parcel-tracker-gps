package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.service.CourierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.context.annotation.Import;
import com.parceltracker.config.SecurityConfig;
import com.parceltracker.repository.CourierAccountRepository;

@WebMvcTest(CourierController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "courier-1", roles = "COURIER")
class CourierControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private CourierService service;
    @MockBean private CourierAccountRepository accounts;

    @Test
    void updatesCourierDeliveryStatus() throws Exception {
        Parcel parcel = new Parcel();
        parcel.setId("parcel-1");
        parcel.setStatus(ParcelStatus.PICKED_UP);
        when(service.updateStatus("courier-1", "parcel-1", ParcelStatus.PICKED_UP)).thenReturn(parcel);

        mvc.perform(put("/api/couriers/courier-1/deliveries/parcel-1/status")
                        .with(csrf())
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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid delivery status transition"));
    }

    @Test
    void acceptsDeliveryRequest() throws Exception {
        Parcel parcel = new Parcel();
        parcel.setStatus(ParcelStatus.ACCEPTED);
        when(service.decideRequest("courier-1", "parcel-1", true)).thenReturn(parcel);

        mvc.perform(put("/api/couriers/courier-1/deliveries/parcel-1/decision")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void submitsProofOfDelivery() throws Exception {
        Parcel parcel = new Parcel();
        parcel.setStatus(ParcelStatus.DELIVERED);
        when(service.completeDelivery(eq("courier-1"), eq("parcel-1"), any())).thenReturn(parcel);

        mvc.perform(post("/api/couriers/courier-1/deliveries/parcel-1/proof")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barcode\":\"BAR-123\",\"tipAmount\":2.5,\"payoutAmount\":8.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void returnsEarningsSummary() throws Exception {
        when(service.getEarnings("courier-1"))
                .thenReturn(new com.parceltracker.dto.CourierEarnings(2, 3.5, 20.0));

        mvc.perform(get("/api/couriers/courier-1/earnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedDeliveries").value(2))
                .andExpect(jsonPath("$.tips").value(3.5))
                .andExpect(jsonPath("$.payouts").value(20.0));
    }

    @Test
    @WithAnonymousUser
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/couriers/courier-1/me"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void preventsAccessToAnotherCouriersDeliveries() throws Exception {
        mvc.perform(get("/api/couriers/courier-2/deliveries"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsStateChangesWithoutCsrfToken() throws Exception {
        mvc.perform(put("/api/couriers/courier-1/deliveries/parcel-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
