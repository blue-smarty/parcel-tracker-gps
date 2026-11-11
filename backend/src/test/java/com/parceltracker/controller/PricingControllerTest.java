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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;

@WebMvcTest(PricingController.class)
@Import(SecurityConfig.class)
class PricingControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private CourierAccountRepository courierAccounts;
    @MockBean private PricingSettingsRepository pricingSettings;

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
