package com.parceltracker.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.service.CourierPushService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourierPushController.class)
@Import(SecurityConfig.class)
class CourierPushControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CourierPushService push;
    @MockitoBean CourierAccountRepository couriers;

    @Test
    @WithMockUser(username = "courier-1", roles = "COURIER")
    void courierCanOnlyManageOwnSubscription() throws Exception {
        when(push.publicKey()).thenReturn("key");
        mvc.perform(get("/api/couriers/courier-1/push-key")).andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("key"));
        mvc.perform(get("/api/couriers/courier-2/push-key")).andExpect(status().isForbidden());
        mvc.perform(post("/api/couriers/courier-1/push-subscription").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"https://fcm.googleapis.com/x\",\"keys\":{\"p256dh\":\"key\",\"auth\":\"auth\"}}"))
                .andExpect(status().isNoContent());
        verify(push).subscribe("courier-1", "https://fcm.googleapis.com/x", "key", "auth");
        mvc.perform(post("/api/couriers/courier-2/push-subscription").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"https://fcm.googleapis.com/x\",\"keys\":{\"p256dh\":\"key\",\"auth\":\"auth\"}}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/couriers/courier-1/push-unsubscribe")
                .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"https://fcm.googleapis.com/x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/couriers/courier-1/push-message").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanSendOnlyToExistingCourier() throws Exception {
        when(couriers.existsById("courier-1")).thenReturn(true);
        when(push.send("courier-1", "Hello")).thenReturn(1);
        mvc.perform(post("/api/admin/couriers/courier-1/push-message").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\" Hello \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(1));
        mvc.perform(post("/api/admin/couriers/missing/push-message").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isBadRequest());
        verify(push, never()).send(eq("missing"), anyString());
    }
}
