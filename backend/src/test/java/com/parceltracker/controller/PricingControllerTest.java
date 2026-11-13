package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.PricingSettings;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.PricingSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;

@WebMvcTest(PricingController.class)
@Import(SecurityConfig.class)
class PricingControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CourierAccountRepository courierAccounts;
    @MockitoBean private PricingSettingsRepository pricingSettings;

    @Test
    @WithAnonymousUser
    void anonymousVisitorCanReadPricingForQuote() throws Exception {
        mvc.perform(get("/api/pricing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseFee").value(5.0));
    }

    @Test
    void customerCanReadDefaultPricing() throws Exception {
        mvc.perform(get("/api/pricing").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseFee").value(5.0))
                .andExpect(jsonPath("$.perKmRate").value(1.2))
                .andExpect(jsonPath("$.perKgRate").value(0.5))
                .andExpect(jsonPath("$.sameDayMultiplier").value(1.75));
    }

    @Test
    void adminCanUpdateSavedPricing() throws Exception {
        when(pricingSettings.findById(PricingSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(pricingSettings.save(any(PricingSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(put("/api/admin/pricing").with(user("ops").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseFee":7,"perKmRate":2,"perKgRate":1.5,"sameDayMultiplier":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseFee").value(7))
                .andExpect(jsonPath("$.sameDayMultiplier").value(2));
        verify(pricingSettings).save(any(PricingSettings.class));
    }

    @Test
    void adminCanSaveRadiusBandsAndRejectsInvalidBandOrder() throws Exception {
        when(pricingSettings.findById(PricingSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(pricingSettings.save(any(PricingSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(put("/api/admin/pricing").with(user("ops").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseFee":5,"perKmRate":1.2,"perKgRate":0.5,"sameDayMultiplier":1.75,
                                 "pricingMode":"RADIUS","radiusBands":[
                                   {"upToKm":5,"distanceFee":3},{"upToKm":null,"distanceFee":8}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricingMode").value("RADIUS"))
                .andExpect(jsonPath("$.radiusBands[1].distanceFee").value(8));
        mvc.perform(put("/api/admin/pricing").with(user("ops").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseFee":5,"perKmRate":1.2,"perKgRate":0.5,"sameDayMultiplier":1.75,
                                 "pricingMode":"RADIUS","radiusBands":[
                                   {"upToKm":10,"distanceFee":3},{"upToKm":5,"distanceFee":8}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidRatesAndNonAdminUpdates() throws Exception {
        mvc.perform(put("/api/admin/pricing").with(user("ops").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseFee":-1,"perKmRate":2,"perKgRate":1.5,"sameDayMultiplier":2}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/pricing").with(user("customer").roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseFee":7,"perKmRate":2,"perKgRate":1.5,"sameDayMultiplier":2}
                                """))
                .andExpect(status().isForbidden());
        verify(pricingSettings, never()).save(any(PricingSettings.class));
    }
}
