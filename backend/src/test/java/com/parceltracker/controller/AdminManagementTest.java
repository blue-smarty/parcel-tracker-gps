package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.BrandingSettings;
import com.parceltracker.model.CourierAccount;
import com.parceltracker.repository.BrandingRepository;
import com.parceltracker.repository.CourierAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AdminCourierController.class, BrandingController.class})
@Import(SecurityConfig.class)
class AdminManagementTest {
    @Autowired private MockMvc mvc;
    @MockBean private CourierAccountRepository accounts;
    @MockBean private BrandingRepository branding;

    @Test
    void adminCreatesCourier() throws Exception {
        mvc.perform(post("/api/admin/couriers").with(user("ops").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":\"courier-7\",\"password\":\"long-enough-pass\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courierId").value("courier-7"));
        verify(accounts).save(any(CourierAccount.class));
    }

    @Test
    void rejectsShortPasswordAndDuplicate() throws Exception {
        mvc.perform(post("/api/admin/couriers").with(user("ops").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":\"courier-7\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        when(accounts.existsById("courier-7")).thenReturn(true);
        mvc.perform(post("/api/admin/couriers").with(user("ops").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":\"courier-7\",\"password\":\"long-enough-pass\"}"))
                .andExpect(status().isConflict());
        verify(accounts, never()).save(any());
    }

    @Test
    void adminResetsPassword() throws Exception {
        when(accounts.existsById("courier-7")).thenReturn(true);
        mvc.perform(put("/api/admin/couriers/courier-7/password").with(user("ops").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"another-long-pass\"}"))
                .andExpect(status().isOk());
        verify(accounts).save(any(CourierAccount.class));
    }

    @Test
    void courierCannotManageCouriers() throws Exception {
        mvc.perform(get("/api/admin/couriers").with(user("courier-1").roles("COURIER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void brandingIsPublicWithDefaults() throws Exception {
        when(branding.findById("default")).thenReturn(Optional.empty());
        mvc.perform(get("/api/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColor").value("#1f4e79"));
    }

    @Test
    void adminUpdatesBrandingAndValidates() throws Exception {
        when(branding.save(any(BrandingSettings.class))).thenAnswer(i -> i.getArgument(0));
        mvc.perform(put("/api/admin/branding").with(user("ops").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appName\":\"Acme\",\"primaryColor\":\"#112233\",\"accentColor\":\"#445566\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("Acme"));
        mvc.perform(put("/api/admin/branding").with(user("ops").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appName\":\"Acme\",\"primaryColor\":\"red\",\"accentColor\":\"#445566\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/branding").with(user("ops").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appName\":\"Acme\",\"primaryColor\":\"#112233\",\"accentColor\":\"#445566\","
                                + "\"logoContentType\":\"image/svg+xml\",\"logoBase64\":\"AAAA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void courierCannotUpdateBranding() throws Exception {
        mvc.perform(put("/api/admin/branding").with(user("courier-1").roles("COURIER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
