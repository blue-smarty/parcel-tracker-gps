package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.ServiceAreaSettings;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.ServiceAreaSettingsRepository;
import com.parceltracker.service.AustralianCouncilLookup;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ServiceAreaSettingsController.class)
@Import(SecurityConfig.class)
class ServiceAreaSettingsControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private ServiceAreaSettingsRepository settings;
    @MockBean private CourierAccountRepository couriers;
    @MockBean private JdbcTemplate jdbc;
    @MockBean private AustralianCouncilLookup councilLookup;

    @Test
    void onlyAdminCanChangeSettingWithCsrf() throws Exception {
        when(settings.findForUpdate()).thenReturn(Optional.of(new ServiceAreaSettings()));
        String body = "{\"melbourneMetroOnly\":true}";
        mvc.perform(put("/api/admin/service-area").with(user("dispatcher").roles("DISPATCHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/service-area").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(settings);
        mvc.perform(put("/api/admin/service-area").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.melbourneMetroOnly").value(true));
        verify(settings).save(any(ServiceAreaSettings.class));
        verify(jdbc).update(anyString(), any(), eq("admin"), eq(true), any());
    }

    @Test
    void rejectsEmptyCustomCoverage() throws Exception {
        when(settings.findForUpdate()).thenReturn(Optional.of(new ServiceAreaSettings()));
        mvc.perform(put("/api/admin/service-area").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"coverageMode\":\"CUSTOM\"}"))
                .andExpect(status().isBadRequest());
        verify(settings, never()).save(any());
    }

    @Test
    void acceptsCustomStateAndPolygonAndAuditsConfiguration() throws Exception {
        when(settings.findForUpdate()).thenReturn(Optional.of(new ServiceAreaSettings()));
        mvc.perform(put("/api/admin/service-area").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"coverageMode":"CUSTOM","states":["2"],"councils":[],
                         "polygons":[[[-38,144],[-38,145],[-37,145]]]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverageMode").value("CUSTOM"))
                .andExpect(jsonPath("$.states[0]").value("2"))
                .andExpect(jsonPath("$.polygons[0][0][0]").value(-38));
        verify(settings).save(any(ServiceAreaSettings.class));
        verify(jdbc).update(anyString(), any(), eq("admin"), eq(false), contains("\"coverageMode\":\"CUSTOM\""));
    }

    @Test
    void rejectsMalformedPolygon() throws Exception {
        when(settings.findForUpdate()).thenReturn(Optional.of(new ServiceAreaSettings()));
        mvc.perform(put("/api/admin/service-area").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"coverageMode\":\"CUSTOM\",\"polygons\":[[[-38,144],[-38,145]]]}"))
                .andExpect(status().isBadRequest());
        verify(settings, never()).save(any());
    }
}
