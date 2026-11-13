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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    @MockitoBean private CourierService service;
    @MockitoBean private com.parceltracker.service.DriverActionService actions;
    @MockitoBean private CourierAccountRepository accounts;

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
                        .content("{\"barcode\":\"BAR-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    @WithAnonymousUser
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/couriers/courier-1/me"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void returnsOwnIdentityForSignIn() throws Exception {
        mvc.perform(get("/api/couriers/courier-1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value("courier-1"));
    }

    @Test
    void rejectsIdentityOfAnotherCourier() throws Exception {
        mvc.perform(get("/api/couriers/courier-2/me"))
                .andExpect(status().isForbidden());
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
