package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.ServiceLevelSettings;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.ServiceLevelSettingsRepository;
import com.parceltracker.service.ConfigurationHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ServiceLevelSettingsController.class)
@Import(SecurityConfig.class)
class ServiceLevelSettingsControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CourierAccountRepository couriers;
    @MockitoBean private ServiceLevelSettingsRepository settings;
    @MockitoBean private ConfigurationHistoryService history;

    @Test
    void defaultsArePublicAndOnlyAdminsCanChangeValidSettings() throws Exception {
        when(settings.save(any(ServiceLevelSettings.class))).thenAnswer(call -> call.getArgument(0));
        mvc.perform(get("/api/service-levels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generalMinutes").value(240))
                .andExpect(jsonPath("$.expressMinutes").value(120))
                .andExpect(jsonPath("$.targetStart").value("BOOKING"));
        String valid = "{\"generalMinutes\":300,\"expressMinutes\":90,\"targetStart\":\"PICKUP\"}";
        mvc.perform(put("/api/admin/service-levels").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetStart").value("PICKUP"));
        mvc.perform(put("/api/admin/service-levels").with(user("dispatcher").roles("DISPATCHER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/service-levels").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generalMinutes\":60,\"expressMinutes\":120,\"targetStart\":\"BOOKING\"}"))
                .andExpect(status().isBadRequest());
    }
}
