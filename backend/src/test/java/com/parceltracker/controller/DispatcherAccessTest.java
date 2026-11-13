package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.DispatcherAccount;
import com.parceltracker.model.Parcel;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.CustomerAccountRepository;
import com.parceltracker.repository.DispatcherAccountRepository;
import com.parceltracker.service.ParcelService;
import com.parceltracker.service.PasswordResetService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AuthController.class, AdminDispatcherController.class, ParcelController.class, DispatchController.class})
@Import(SecurityConfig.class)
class DispatcherAccessTest {
    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder encoder;
    @MockitoBean private CourierAccountRepository couriers;
    @MockitoBean private CustomerAccountRepository customers;
    @MockitoBean private DispatcherAccountRepository dispatchers;
    @MockitoBean private ParcelService parcels;
    @MockitoBean private PasswordResetService resets;

    @Test
    void dispatcherAuthenticatesAndGetsOperationalAccessOnly() throws Exception {
        when(dispatchers.findById("dispatch-1")).thenReturn(Optional.of(
                new DispatcherAccount("dispatch-1", encoder.encode("a-strong-password"))));
        mvc.perform(get("/api/auth/me").with(httpBasic("dispatch-1", "a-strong-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("DISPATCHER"));
        mvc.perform(get("/api/parcels").with(user("dispatch-1").roles("DISPATCHER"))).andExpect(status().isOk());
        mvc.perform(get("/api/dispatch/couriers").with(user("dispatch-1").roles("DISPATCHER"))).andExpect(status().isOk());
        when(parcels.get("p1")).thenReturn(new Parcel());
        when(couriers.existsById("c1")).thenReturn(true);
        mvc.perform(get("/api/parcels/p1").with(user("dispatch-1").roles("DISPATCHER"))).andExpect(status().isOk());
        mvc.perform(put("/api/parcels/p1/courier").with(user("dispatch-1").roles("DISPATCHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"courierId\":\"c1\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/dispatchers").with(user("dispatch-1").roles("DISPATCHER"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/pricing").with(user("dispatch-1").roles("DISPATCHER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/parcels/p1").with(user("dispatch-1").roles("DISPATCHER")).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void deliveryCoveragePageIsReachableButItsSettingsRemainAdminOnly() throws Exception {
        mvc.perform(get("/delivery-coverage.html")).andExpect(status().isOk());
        mvc.perform(get("/admin-coverage.js")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/service-area").with(user("dispatch-1").roles("DISPATCHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesAndRevokesDispatcherButOtherRolesCannot() throws Exception {
        String body = "{\"username\":\"Dispatch-1\",\"password\":\"a-strong-password\"}";
        mvc.perform(post("/api/admin/dispatchers").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("dispatch-1"));
        verify(dispatchers).save(any(DispatcherAccount.class));
        mvc.perform(post("/api/admin/dispatchers").with(user("dispatch-1").roles("DISPATCHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        when(dispatchers.existsById("dispatch-1")).thenReturn(true);
        mvc.perform(delete("/api/admin/dispatchers/dispatch-1").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        verify(dispatchers).deleteById("dispatch-1");
    }

    @Test
    void rejectsCourierNameCollisions() throws Exception {
        when(couriers.existsByCourierIdIgnoreCase("driver1")).thenReturn(true);
        mvc.perform(post("/api/admin/dispatchers").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"driver1\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void dispatcherCanCreateAdhocButCannotAssignAnUnknownCourier() throws Exception {
        when(couriers.existsById("c1")).thenReturn(true);
        when(parcels.createAssigned(any(Parcel.class), org.mockito.ArgumentMatchers.eq("c1")))
                .thenAnswer(call -> call.getArgument(0));
        mvc.perform(post("/api/parcels/adhoc").with(user("d").roles("DISPATCHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sender\":\"Alice\",\"customerContact\":\"555\",\"pickupAddress\":\"1 Main\","
                        + "\"recipient\":\"Bob\",\"destination\":\"2 Oak\",\"courierId\":\"c1\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/parcels/p1/courier").with(user("d").roles("DISPATCHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"courierId\":\"unknown\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanResetDispatcherPasswordAndMissingAccountsAreNotCreated() throws Exception {
        when(dispatchers.existsById("dispatch-1")).thenReturn(true);
        mvc.perform(put("/api/admin/dispatchers/dispatch-1/password").with(user("admin").roles("ADMIN"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"new-strong-password\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/admin/dispatchers/missing/password").with(user("admin").roles("ADMIN"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"new-strong-password\"}")).andExpect(status().isNotFound());
    }
}
