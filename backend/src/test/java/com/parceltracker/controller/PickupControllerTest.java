package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.dto.PickupRequest;
import com.parceltracker.model.Pickup;
import com.parceltracker.service.PickupService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.parceltracker.config.SecurityConfig;
import com.parceltracker.repository.CourierAccountRepository;

@WebMvcTest(PickupController.class)
@Import(SecurityConfig.class)
class PickupControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private PickupService service;
    @MockBean private CourierAccountRepository accounts;

    private String json(String date, String weight, String phone) {
        return "{\"contactName\":\"Ann\",\"contactPhone\":\"" + phone + "\",\"street\":\"1 Main St\","
                + "\"city\":\"Sydney\",\"postalCode\":\"2000\",\"parcelDescription\":\"Books\","
                + "\"weightKg\":" + weight + ",\"pickupDate\":\"" + date + "\",\"timeWindow\":\"09:00-12:00\"}";
    }

    @Test
    void createReturns201() throws Exception {
        Pickup p = new Pickup();
        p.setId("abc");
        when(service.create(any(PickupRequest.class))).thenReturn(p);
        mvc.perform(post("/api/pickups").contentType(MediaType.APPLICATION_JSON)
                        .content(json(LocalDate.now().plusDays(1).toString(), "2.5", "+61 400 000 000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("abc"))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void invalidReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/pickups").contentType(MediaType.APPLICATION_JSON)
                        .content(json(LocalDate.now().minusDays(1).toString(), "0", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pickupDate").exists())
                .andExpect(jsonPath("$.errors.weightKg").exists())
                .andExpect(jsonPath("$.errors.contactPhone").exists());
    }
}
