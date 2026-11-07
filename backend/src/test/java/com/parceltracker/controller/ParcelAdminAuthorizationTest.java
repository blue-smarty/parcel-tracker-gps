package com.parceltracker.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.Parcel;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.service.ParcelService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    private static final String ADHOC = """
            {"sender":" Alice ","customerContact":" 1234567 ","pickupAddress":" 1 Main St ",
             "recipient":" Bob ","destination":" 2 Oak St ","courierId":" courier-1 "}
            """;

    @Test
    void adminCreatesAssignedParcelWithPickupDetails() throws Exception {
        when(accounts.existsById("courier-1")).thenReturn(true);
        when(service.createAssigned(any(Parcel.class), eq("courier-1"))).thenAnswer(call -> call.getArgument(0));
        mvc.perform(post("/api/parcels/adhoc").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ADHOC))
                .andExpect(status().isCreated());
        ArgumentCaptor<Parcel> parcel = ArgumentCaptor.forClass(Parcel.class);
        verify(service).createAssigned(parcel.capture(), eq("courier-1"));
        assertEquals("Alice", parcel.getValue().getSender());
        assertEquals("1234567", parcel.getValue().getCustomerContact());
        assertEquals("1 Main St", parcel.getValue().getPickupAddress());
        assertEquals("Bob", parcel.getValue().getRecipient());
        assertEquals("2 Oak St", parcel.getValue().getDestination());
    }

    @Test
    void adhocRejectsUnknownCourierAndInvalidFields() throws Exception {
        mvc.perform(post("/api/parcels/adhoc").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ADHOC))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/parcels/adhoc").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ADHOC.replace(" Alice ", "   ")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void adhocRequiresAdminAndCsrf() throws Exception {
        mvc.perform(post("/api/parcels/adhoc").with(user("customer").roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ADHOC))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/parcels/adhoc").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(ADHOC))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/parcels/adhoc").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ADHOC))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

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

    @Test
    void rejectsAnonymousParcelListing() throws Exception {
        mvc.perform(get("/api/parcels"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void customerCanOnlyReadOwnedParcel() throws Exception {
        Parcel parcel = new Parcel();
        parcel.setCustomerEmail("other@example.com");
        when(service.get("parcel-1")).thenReturn(parcel);

        mvc.perform(get("/api/parcels/parcel-1")
                        .with(user("customer@example.com").roles("CUSTOMER")))
                .andExpect(status().isNotFound());

        verify(service).get("parcel-1");
    }

    @Test
    void customerParcelListIsScopedToAuthenticatedAccount() throws Exception {
        when(service.findByCustomerEmail("customer@example.com")).thenReturn(java.util.List.of());

        mvc.perform(get("/api/parcels/mine")
                        .with(user("customer@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk());

        verify(service).findByCustomerEmail("customer@example.com");
    }

    @Test
    void parcelCreationRequiresCustomerOrAdmin() throws Exception {
        mvc.perform(post("/api/parcels").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sender\":\"A\",\"recipient\":\"B\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}
