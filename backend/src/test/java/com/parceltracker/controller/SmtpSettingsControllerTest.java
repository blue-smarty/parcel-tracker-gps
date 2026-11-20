package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.SmtpSettings;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.service.SmtpSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SmtpSettingsController.class)
@Import(SecurityConfig.class)
class SmtpSettingsControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private SmtpSettingsService smtp;
    @MockitoBean private CourierAccountRepository couriers;
    private static final String REQUEST = """
            {"host":"smtp.example.com","port":587,"username":"mailer","fromAddress":"mail@example.com",
             "security":"STARTTLS","authentication":true}
            """;

    @Test
    void adminCanSaveAndClearWithoutExposingPassword() throws Exception {
        when(smtp.save(any(SmtpSettings.class))).thenAnswer(call -> call.getArgument(0));
        mvc.perform(put("/api/admin/smtp").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value("smtp.example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(delete("/api/admin/smtp").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        verify(smtp).clear();
    }

    @Test
    void rejectsUnauthorizedOrInsecureSettings() throws Exception {
        mvc.perform(get("/api/admin/smtp").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/smtp").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/smtp").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST.replace("STARTTLS", "NONE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SMTP authentication requires a username and TLS."));
        mvc.perform(put("/api/admin/smtp").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST.replace("587", "65536")))
                .andExpect(status().isBadRequest());
    }
}
