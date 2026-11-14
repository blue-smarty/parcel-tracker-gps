package com.parceltracker.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.CustomerAddress;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.CustomerAccountRepository;
import com.parceltracker.repository.CustomerAddressRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerAddressController.class)
@Import(SecurityConfig.class)
class CustomerAddressControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CustomerAddressRepository addresses;
    @MockitoBean private CustomerAccountRepository customers;
    @MockitoBean private CourierAccountRepository couriers;

    @Test
    void customerReadsOnlyTheirOwnAddressBook() throws Exception {
        when(addresses.findAllByCustomerEmailOrderByLabelAsc("ann@example.com"))
                .thenReturn(List.of(new CustomerAddress("ann@example.com", "Home", "Ann", "123", "1 Main St")));

        mvc.perform(get("/api/addresses").with(user("ann@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk());

        verify(addresses).findAllByCustomerEmailOrderByLabelAsc("ann@example.com");
    }

    @Test
    void staffCanManageAddressesForAnExistingCustomer() throws Exception {
        when(customers.existsById("ann@example.com")).thenReturn(true);

        mvc.perform(post("/api/dispatch/customers/ann@example.com/addresses")
                        .with(user("dispatch").roles("DISPATCHER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Home","contactName":"Ann","contactPhone":"123","address":"1 Main St"}
                                """))
                .andExpect(status().isCreated());

        verify(customers).existsById("ann@example.com");
        verify(addresses).save(org.mockito.ArgumentMatchers.any(CustomerAddress.class));
    }

    @Test
    void customersCannotAccessStaffAddressBooks() throws Exception {
        mvc.perform(get("/api/dispatch/customers/ann@example.com/addresses")
                        .with(user("ann@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(addresses, customers);
    }

    @Test
    void mutationsRequireCsrfAndCustomerOwnership() throws Exception {
        mvc.perform(post("/api/addresses").with(user("ann@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Home","contactName":"Ann","contactPhone":"123","address":"1 Main St"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/addresses/address-1").with(user("ann@example.com").roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isNotFound());

        verify(addresses).findByIdAndCustomerEmail("address-1", "ann@example.com");
    }
}
