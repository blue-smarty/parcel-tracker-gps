package com.parceltracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.PricingSettings;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.PricingSettingsRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(classes = PricingHttpTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"parcel.auth.admin-username=test-admin", "parcel.auth.admin-password=example-password"})
class PricingHttpTest {
    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @Import({SecurityConfig.class, PricingController.class, CsrfController.class})
    static class App {}

    @LocalServerPort private int port;
    @MockBean private PricingSettingsRepository settings;
    @MockBean private CourierAccountRepository couriers;

    @Test
    void realHttpPricingSaveUsesCookieAndReportsBadInput() throws Exception {
        when(settings.save(any(PricingSettings.class))).thenAnswer(call -> call.getArgument(0));
        HttpClient client = HttpClient.newHttpClient();
        String base = "http://localhost:" + port;
        var csrf = client.send(HttpRequest.newBuilder(URI.create(base + "/api/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, csrf.statusCode());
        String token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(csrf.body()).path("token").asText();
        String cookie = csrf.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
        String credentials = Base64.getEncoder().encodeToString("test-admin:example-password".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(base + "/api/admin/pricing"))
                .header("Authorization", "Basic " + credentials).header("Cookie", cookie)
                .header("X-XSRF-TOKEN", token).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"baseFee\":7,\"perKmRate\":2,\"perKgRate\":1,\"sameDayMultiplier\":2}")).build();
        assertEquals(200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        var invalid = HttpRequest.newBuilder(URI.create(base + "/api/admin/pricing"))
                .header("Authorization", "Basic " + credentials).header("Cookie", cookie)
                .header("X-XSRF-TOKEN", token).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"baseFee\":-1,\"perKmRate\":2,\"perKgRate\":1,\"sameDayMultiplier\":2}")).build();
        assertEquals(400, client.send(invalid, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void missingCsrfCookieIsReportedAsForbidden() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String base = "http://localhost:" + port;
        var request = HttpRequest.newBuilder(URI.create(base + "/api/admin/pricing"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        "test-admin:example-password".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .header("X-XSRF-TOKEN", "stale").header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{}")).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
        assertTrue(response.body().contains("CSRF_INVALID"));
    }

    @Test
    void invalidCredentialsRemainUnauthorizedWithValidCsrf() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String base = "http://localhost:" + port;
        var csrf = client.send(HttpRequest.newBuilder(URI.create(base + "/api/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(csrf.body()).path("token").asText();
        String cookie = csrf.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
        var request = HttpRequest.newBuilder(URI.create(base + "/api/admin/pricing"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        "test-admin:wrong-password".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .header("Cookie", cookie).header("X-XSRF-TOKEN", token).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertEquals(401, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void serverErrorsAreNotMaskedAsAuthenticationFailures() throws Exception {
        when(settings.save(any(PricingSettings.class))).thenThrow(new IllegalStateException("database failure"));
        HttpClient client = HttpClient.newHttpClient();
        String base = "http://localhost:" + port;
        var csrf = client.send(HttpRequest.newBuilder(URI.create(base + "/api/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(csrf.body()).path("token").asText();
        String cookie = csrf.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
        var request = HttpRequest.newBuilder(URI.create(base + "/api/admin/pricing"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        "test-admin:example-password".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .header("Cookie", cookie).header("X-XSRF-TOKEN", token).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(
                        "{\"baseFee\":7,\"perKmRate\":2,\"perKgRate\":1,\"sameDayMultiplier\":2}")).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(500, response.statusCode());
        assertTrue(!response.body().contains("database failure"));
    }
}
