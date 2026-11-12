package com.parceltracker.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.service.TomTomSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminIntegrationController.class)
@Import(SecurityConfig.class)
class AdminIntegrationControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private CourierAccountRepository accounts;
    @MockBean private TomTomSettingsService settings;

    @Test
    void adminCanSaveAndReadStatusWithoutKeyDisclosure() throws Exception {
        when(settings.status()).thenReturn(new TomTomSettingsService.Status(true, true, false, true));
        mvc.perform(put("/api/admin/integrations/tomtom").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(settings).save("secret");
        mvc.perform(delete("/api/admin/integrations/tomtom").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
        verify(settings).remove();
    }

    @Test
    void nonAdminsCannotReadOrChangeSettings() throws Exception {
        mvc.perform(get("/api/admin/integrations/tomtom").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/integrations/tomtom").with(user("dispatcher").roles("DISPATCHER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"secret\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/integrations/tomtom").with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(settings);
    }
}
