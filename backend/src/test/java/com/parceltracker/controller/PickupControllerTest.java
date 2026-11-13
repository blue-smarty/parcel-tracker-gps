package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.dto.PickupRequest;
import com.parceltracker.model.Pickup;
import com.parceltracker.service.PickupService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.parceltracker.config.SecurityConfig;
import com.parceltracker.repository.CourierAccountRepository;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(PickupController.class)
@Import(SecurityConfig.class)
class PickupControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private PickupService service;
    @MockitoBean private CourierAccountRepository accounts;

    private String json(String date, String weight, String phone) {
        return "{\"contactName\":\"Ann\",\"contactPhone\":\"" + phone + "\",\"street\":\"1 Main St\","
                + "\"city\":\"Sydney\",\"postalCode\":\"2000\",\"parcelDescription\":\"Books\","
                + "\"weightKg\":" + weight + ",\"pickupDate\":\"" + date + "\",\"timeWindow\":\"09:00-12:00\"}";
    }

    @Test
    void createReturns201() throws Exception {
        Pickup p = new Pickup();
        p.setId("abc");
        when(service.create(any(PickupRequest.class), eq("ann@example.com"))).thenReturn(p);
        mvc.perform(post("/api/pickups").with(user("ann@example.com").roles("CUSTOMER")).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(LocalDate.now().plusDays(1).toString(), "2.5", "+61 400 000 000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("abc"))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void invalidReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/pickups").with(user("ann@example.com").roles("CUSTOMER")).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(LocalDate.now().minusDays(1).toString(), "0", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pickupDate").exists())
                .andExpect(jsonPath("$.errors.weightKg").exists())
                .andExpect(jsonPath("$.errors.contactPhone").exists());
    }

    @Test
    void rejectsAnonymousPickupRequests() throws Exception {
        mvc.perform(post("/api/pickups").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(LocalDate.now().plusDays(1).toString(), "2.5", "+61 400 000 000")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotReadAnotherCustomersPickup() throws Exception {
        Pickup pickup = new Pickup();
        pickup.setCustomerEmail("owner@example.com");
        when(service.get("pickup-1")).thenReturn(pickup);

        mvc.perform(get("/api/pickups/pickup-1")
                        .with(user("other@example.com").roles("CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void pickupOwnerCanReadWithoutExposingOwnershipField() throws Exception {
        Pickup pickup = new Pickup();
        pickup.setCustomerEmail("owner@example.com");
        pickup.setStatus(com.parceltracker.model.PickupStatus.REQUESTED);
        when(service.get("pickup-1")).thenReturn(pickup);

        mvc.perform(get("/api/pickups/pickup-1")
                        .with(user("owner@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerEmail").doesNotExist());
    }
}
