package com.parceltracker.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.service.CourierRouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@WebMvcTest(CourierRouteController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "courier-1", roles = "COURIER")
class CourierRouteControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CourierRouteService routes;
    @MockitoBean private CourierAccountRepository accounts;

    @Test
    void preventsAccessToAnotherCouriersRoute() throws Exception {
        mvc.perform(post("/api/couriers/courier-2/route").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":-37.8,\"longitude\":144.9}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(routes);
    }

    @Test
    void rejectsInvalidCoordinates() throws Exception {
        mvc.perform(post("/api/couriers/courier-1/route").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":91,\"longitude\":144.9}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(routes);
    }

    @Test
    void returnsSafeProviderErrorMessage() throws Exception {
        when(routes.calculate("courier-1", -37.8, 144.9))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Route lookup is not configured."));
        mvc.perform(post("/api/couriers/courier-1/route").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":-37.8,\"longitude\":144.9}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Route lookup is not configured."));
    }
}
