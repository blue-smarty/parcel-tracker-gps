package com.parceltracker.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.Parcel;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.service.ParcelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ParcelController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parcel.auth.admin-username=operations",
        "parcel.auth.admin-password=correct-horse-battery"
})
class ParcelAdminAuthorizationTest {
    @Autowired private MockMvc mvc;
    @MockBean private ParcelService service;
    @MockBean private CourierAccountRepository accounts;

    @Test
    void allowsAdminToAssignCourierWithCsrf() throws Exception {
        when(service.assignCourier("parcel-1", "courier-1")).thenReturn(new Parcel());

        mvc.perform(put("/api/parcels/parcel-1/courier")
                        .with(httpBasic("operations", "correct-horse-battery"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":\"courier-1\"}"))
                .andExpect(status().isOk());

        verify(service).assignCourier("parcel-1", "courier-1");
    }

    @Test
    void rejectsParcelAssignmentWithoutAdminCredentials() throws Exception {
        mvc.perform(put("/api/parcels/parcel-1/courier")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":\"courier-1\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAdminMutationWithoutCsrf() throws Exception {
        mvc.perform(put("/api/parcels/parcel-1/courier")
                        .with(httpBasic("operations", "correct-horse-battery"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":\"courier-1\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }
}
