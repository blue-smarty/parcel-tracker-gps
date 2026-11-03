package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.CustomerAccount;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.CustomerAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder encoder;
    @MockBean private CourierAccountRepository couriers;
    @MockBean private CustomerAccountRepository customers;

    private static final String BODY = "{\"name\":\"Ada\",\"email\":\"Ada@Example.com\",\"password\":\"correct horse battery\"}";

    @Test
    void registersCustomerWithNormalizedEmail() throws Exception {
        when(customers.save(any(CustomerAccount.class))).thenAnswer(i -> i.getArgument(0));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("ada@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        verify(customers).save(any(CustomerAccount.class));
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        when(customers.existsById("ada@example.com")).thenReturn(true);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsShortPassword() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"email\":\"a@b.co\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerCanSignInAndMeReportsRole() throws Exception {
        when(couriers.findById("ada@example.com")).thenReturn(Optional.empty());
        when(customers.findById("ada@example.com")).thenReturn(Optional.of(
                new CustomerAccount("ada@example.com", "Ada", encoder.encode("correct horse battery"))));
        mvc.perform(get("/api/auth/me").with(httpBasic("ada@example.com", "correct horse battery")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.name").value("Ada"));
        mvc.perform(get("/api/auth/me").with(httpBasic("ada@example.com", "wrong password!!")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
