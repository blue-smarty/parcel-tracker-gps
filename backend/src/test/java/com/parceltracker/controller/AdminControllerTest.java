package com.parceltracker.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.repository.CourierAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parcel.auth.admin-username=operations",
        "parcel.auth.admin-password=correct-horse-battery"
})
class AdminControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private CourierAccountRepository accounts;

    @Test
    void verifiesConfiguredAdminCredentials() throws Exception {
        mvc.perform(get("/api/admin/me").with(httpBasic("operations", "correct-horse-battery")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("operations"));
    }

    @Test
    void allowsRepeatedAdminLogins() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/admin/me").with(httpBasic("operations", "correct-horse-battery")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void rejectsIncorrectAdminPassword() throws Exception {
        mvc.perform(get("/api/admin/me").with(httpBasic("operations", "incorrect-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAdminRole() throws Exception {
        mvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesCourierRole() throws Exception {
        mvc.perform(get("/api/admin/me").with(user("courier-1").roles("COURIER")))
                .andExpect(status().isForbidden());
    }
}
