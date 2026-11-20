package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.OperationalSettings;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.service.ConfigurationHistoryService;
import com.parceltracker.service.OperationalSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OperationalSettingsController.class)
@Import(SecurityConfig.class)
class OperationalSettingsControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private OperationalSettingsService settings;
    @MockitoBean private ConfigurationHistoryService history;
    @MockitoBean private CourierAccountRepository couriers;

    @Test
    void exposesLimitsPubliclyButRequiresAdminToChangeThem() throws Exception {
        when(settings.get()).thenReturn(OperationalSettings.defaults());
        when(history.snapshot(any(OperationalSettings.class), eq(OperationalSettings.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(settings.save(any(OperationalSettings.class))).thenAnswer(call -> call.getArgument(0));
        mvc.perform(get("/api/operational-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxItemsPerBooking").value(200));
        String request = """
                {"maxItemsPerBooking":120,"maxPackageLines":8,"maxQuantityPerItem":15,
                 "maxItemWeightKg":3000,"maxDimensionCm":500,"bookingCutoff":"16:30",
                 "timeZone":"Australia/Sydney","maxScheduleDaysAhead":14,
                 "deliveryWindows":["09:00-11:00","13:00-15:00"],
                 "staleAssignmentEnabled":false,"staleAssignmentMinutes":90,
                 "unassignStaleAssignments":true}
                """;
        mvc.perform(put("/api/admin/operational-settings").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Australia/Sydney"));
        verify(history).record(any(), any(), any(), org.mockito.ArgumentMatchers.eq("admin"));
        mvc.perform(put("/api/admin/operational-settings").with(user("dispatcher").roles("DISPATCHER"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidLimitsBeforeSaving() throws Exception {
        when(settings.get()).thenReturn(OperationalSettings.defaults());
        when(history.snapshot(any(OperationalSettings.class), eq(OperationalSettings.class)))
                .thenAnswer(call -> call.getArgument(0));
        mvc.perform(put("/api/admin/operational-settings").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxItemsPerBooking\":0}"))
                .andExpect(status().isBadRequest());
    }
}
