package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.CourierAccount;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.service.ConfigurationHistoryService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourierCapacityController.class)
@Import(SecurityConfig.class)
class CourierCapacityControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CourierAccountRepository couriers;
    @MockitoBean private ConfigurationHistoryService history;

    @Test
    void savesAValidVehicleCapacityProfileAndAuditsIt() throws Exception {
        CourierAccount courier = new CourierAccount("courier-1", "hash");
        when(couriers.findById("courier-1")).thenReturn(Optional.of(courier));
        when(couriers.save(any(CourierAccount.class))).thenAnswer(call -> call.getArgument(0));
        mvc.perform(put("/api/admin/courier-capacity/courier-1").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\":\"VAN\",\"maxActiveParcels\":8,\"maxPayloadWeightKg\":700}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleType").value("VAN"))
                .andExpect(jsonPath("$.maxPayloadWeightKg").value(700));
        verify(history).record(any(), any(), any(), org.mockito.ArgumentMatchers.eq("admin"));
    }

    @Test
    void rejectsUnsupportedVehicleTypes() throws Exception {
        mvc.perform(put("/api/admin/courier-capacity/courier-1").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\":\"PLANE\",\"maxActiveParcels\":8,\"maxPayloadWeightKg\":700}"))
                .andExpect(status().isBadRequest());
    }
}
