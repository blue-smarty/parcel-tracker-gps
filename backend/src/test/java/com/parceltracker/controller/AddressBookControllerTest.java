package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.CustomerAddress;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.CustomerAccountRepository;
import com.parceltracker.repository.CustomerAddressRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AddressBookController.class)
@Import(SecurityConfig.class)
class AddressBookControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CustomerAddressRepository addresses;
    @MockitoBean private CustomerAccountRepository customers;
    @MockitoBean private CourierAccountRepository couriers;

    @Test
    void customerOwnsEntriesAndCannotTargetAnotherCustomer() throws Exception {
        when(addresses.findByCustomerEmailOrderByLabelAsc("alice@example.com"))
                .thenReturn(List.of(new CustomerAddress("alice@example.com", "Home", "1 Main Street")));
        mvc.perform(get("/api/address-book").with(user("alice@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].address").value("1 Main Street"))
                .andExpect(jsonPath("$[0].customerEmail").doesNotExist());
        mvc.perform(get("/api/address-book?customer=bob@example.com").with(user("alice@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/address-book/bob-id").with(user("alice@example.com").roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isNotFound());
        verify(addresses).findByIdAndCustomerEmail("bob-id", "alice@example.com");
        mvc.perform(put("/api/address-book/bob-id").with(user("alice@example.com").roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Home\",\"address\":\"1 Main\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffCanManageAnExistingCustomerButCouriersCannot() throws Exception {
        when(customers.existsById("alice@example.com")).thenReturn(true);
        when(addresses.save(any(CustomerAddress.class))).thenAnswer(call -> call.getArgument(0));
        mvc.perform(post("/api/address-book?customer=ALICE@example.com").with(user("dispatch").roles("DISPATCHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\" Home \",\"address\":\" 1 Main \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.label").value("Home"));
        CustomerAddress entry = new CustomerAddress("alice@example.com", "Home", "1 Main");
        when(addresses.findByIdAndCustomerEmail("a1", "alice@example.com")).thenReturn(Optional.of(entry));
        mvc.perform(put("/api/address-book/a1?customer=alice@example.com").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Office\",\"address\":\"2 Oak\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").value("2 Oak"));
        mvc.perform(delete("/api/address-book/a1?customer=alice@example.com").with(user("dispatch").roles("DISPATCHER")).with(csrf()))
                .andExpect(status().isNoContent());
        verify(addresses).delete(entry);
        mvc.perform(get("/api/address-book?customer=unknown@example.com").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/address-book").with(user("courier").roles("COURIER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/address-book")).andExpect(status().isUnauthorized());
    }

    @Test
    void validatesEntriesAndRequiresCsrf() throws Exception {
        mvc.perform(post("/api/address-book").with(user("alice@example.com").roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\" \",\"address\":\"1 Main\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/address-book").with(user("alice@example.com").roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Home\",\"address\":\"1 Main\"}"))
                .andExpect(status().isForbidden());
    }
}
