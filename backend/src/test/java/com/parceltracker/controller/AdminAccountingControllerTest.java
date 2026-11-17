package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.Parcel;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.ParcelRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAccountingController.class)
@Import(SecurityConfig.class)
class AdminAccountingControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CourierAccountRepository accounts;
    @MockitoBean private ParcelRepository parcels;

    @Test
    void exportsXeroInvoiceRowsAndNeutralisesFormulas() throws Exception {
        Parcel p = new Parcel();
        p.setTrackingNumber("TRK1");
        p.setSender("=HYPERLINK(\"x\")");
        p.setRecipient("Bob");
        p.setDestination("1 Main St");
        p.setEstimatedPrice(12.5);
        p.setCreatedAt(Instant.parse("2026-03-10T05:00:00Z"));
        when(parcels.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatusNotAndEstimatedPriceNotNullOrderByCreatedAtAsc(
                any(), any(), any())).thenReturn(List.of(p));

        mvc.perform(get("/api/admin/accounting/invoices.csv?from=2026-03-01&to=2026-03-31")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"'=HYPERLINK(\"\"x\"\")\",,\"TRK1\",\"Bob\",2026-03-10,2026-03-10,"
                                + "\"Parcel delivery TRK1 to 1 Main St\",1,12.50,\"200\",\"GST on Income\"")));
    }

    @Test
    void rejectsBadRangeAndNonAdmins() throws Exception {
        mvc.perform(get("/api/admin/accounting/invoices.csv?from=2026-03-31&to=2026-03-01")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/accounting/invoices.csv?from=2026-03-01&to=2026-03-31")
                        .with(user("c").roles("COURIER")))
                .andExpect(status().isForbidden());
    }
}
