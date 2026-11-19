package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.CustomerAccount;
import com.parceltracker.model.Parcel;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.CustomerAccountRepository;
import com.parceltracker.service.CreditApplicationService;
import com.parceltracker.service.ParcelService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CreditApplicationController.class)
@Import(SecurityConfig.class)
class CreditApplicationControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CourierAccountRepository couriers;
    @MockitoBean private CustomerAccountRepository customers;
    @MockitoBean private CreditApplicationService credit;
    @MockitoBean private ParcelService parcels;

    @Test
    void applicationEndpointsEnforceRolesCsrfAndInputValidation() throws Exception {
        String submission = "{\"token\":\"" + "A".repeat(43) + "\",\"businessName\":\"Business\","
                + "\"contactName\":\"Contact\",\"contactEmail\":\"contact@example.com\","
                + "\"contactPhone\":\"123\",\"requestedLimit\":100.00}";
        mvc.perform(post("/api/credit/applications").with(user("client@example.com").roles("CUSTOMER"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(submission)).andExpect(status().isNoContent());
        verify(credit).submit(eq("client@example.com"), any(), any());
        mvc.perform(post("/api/credit/applications").with(user("client@example.com").roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(submission)).andExpect(status().isForbidden());
        mvc.perform(post("/api/credit/applications").with(user("dispatcher").roles("DISPATCHER"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(submission)).andExpect(status().isForbidden());
        mvc.perform(post("/api/credit/applications").with(user("client@example.com").roles("CUSTOMER"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(submission.replace("100.00", "-1")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/credit/applications").with(user("dispatcher").roles("DISPATCHER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/credit/invitations").with(user("client@example.com").roles("CUSTOMER"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"client@example.com\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/credit/applications")).andExpect(status().isUnauthorized());
    }

    @Test
    void dispatchOnlyGetsClassificationNotApplicationDetails() throws Exception {
        Parcel parcel = new Parcel();
        parcel.setCustomerEmail("client@example.com");
        CustomerAccount customer = new CustomerAccount("client@example.com", "Client", "hash");
        customer.setCreditApproved(true);
        when(parcels.get("p1")).thenReturn(parcel);
        when(customers.findById("client@example.com")).thenReturn(Optional.of(customer));
        mvc.perform(get("/api/dispatch/parcels/p1/account-type").with(user("dispatcher").roles("DISPATCHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountType").value("CREDIT"))
                .andExpect(jsonPath("$.customerEmail").doesNotExist());
        customer.setCreditApproved(false);
        mvc.perform(get("/api/dispatch/parcels/p1/account-type").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.accountType").value("NO_CREDIT"));
        parcel.setCustomerEmail(null);
        mvc.perform(get("/api/dispatch/parcels/p1/account-type").with(user("dispatcher").roles("DISPATCHER")))
                .andExpect(jsonPath("$.accountType").value("UNLINKED"));
        mvc.perform(get("/api/dispatch/parcels/p1/account-type").with(user("client@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }
}
