package com.parceltracker.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.CustomerAccount;
import com.parceltracker.model.CustomerCard;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.CourierPushSubscriptionRepository;
import com.parceltracker.repository.CustomerCardRepository;
import com.parceltracker.service.CustomerCardService;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerCardController.class)
@Import({SecurityConfig.class, CustomerCardService.class})
@TestPropertySource(properties = "parcel.cards.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class CustomerCardControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private CustomerCardService service;
    @MockitoBean private CustomerCardRepository repository;
    @MockitoBean private CourierAccountRepository couriers;
    @MockitoBean private CourierPushSubscriptionRepository pushSubscriptions;

    private String storeCard() {
        service.createCard(new CustomerAccount("ada@example.com", "Ada", "hash"), "0400 000 000");
        ArgumentCaptor<CustomerCard> saved = ArgumentCaptor.forClass(CustomerCard.class);
        verify(repository).save(saved.capture());
        org.mockito.Mockito.when(repository.findById("ada@example.com")).thenReturn(Optional.of(saved.getValue()));
        return saved.getValue().getEncryptedCard();
    }

    @Test
    void cardIsEncryptedAtRest() {
        String stored = storeCard();
        assertFalse(new String(Base64.getDecoder().decode(stored)).contains("ada@example.com"));
        assertEquals("Ada", service.find("ada@example.com").orElseThrow().name());
    }

    @Test
    void adminSeesFullCardAndDispatcherSeesLimitedCard() throws Exception {
        storeCard();
        mvc.perform(get("/api/admin/customers/ada@example.com/card").with(user("ops").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.phone").value("0400 000 000"));
        mvc.perform(get("/api/dispatch/customers/ada@example.com/card").with(user("d1").roles("DISPATCHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist());
        mvc.perform(get("/api/admin/customers/ada@example.com/card").with(user("d1").roles("DISPATCHER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dispatch/customers/ada@example.com/card").with(user("c").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }
}
