package com.parceltracker.controller;

import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.repository.ParcelRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Exports booked parcels as a Xero "sales invoices" import CSV. */
@RestController
@RequestMapping("/api/admin/accounting")
public class AdminAccountingController {
    private static final int MAX_RANGE_DAYS = 366;
    private static final String HEADER = "*ContactName,EmailAddress,*InvoiceNumber,Reference,*InvoiceDate,"
            + "*DueDate,Description,*Quantity,*UnitAmount,*AccountCode,*TaxType\n";

    private final ParcelRepository parcels;
    private final ZoneId zone;
    private final String accountCode;
    private final String taxType;

    public AdminAccountingController(ParcelRepository parcels,
            @Value("${parcel.accounting.time-zone:Australia/Sydney}") String zone,
            @Value("${parcel.accounting.account-code:200}") String accountCode,
            @Value("${parcel.accounting.tax-type:GST on Income}") String taxType) {
        this.parcels = parcels;
        this.zone = ZoneId.of(zone);
        this.accountCode = accountCode;
        this.taxType = taxType;
    }

    @GetMapping(value = "/invoices.csv", produces = "text/csv")
    public ResponseEntity<byte[]> invoices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range must be ordered and no longer than " + MAX_RANGE_DAYS + " days");
        }
        List<Parcel> booked = parcels
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatusNotAndEstimatedPriceNotNullOrderByCreatedAtAsc(
                        from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant(),
                        ParcelStatus.CANCELLED);
        StringBuilder csv = new StringBuilder(HEADER);
        for (Parcel p : booked) {
            LocalDate date = p.getCreatedAt().atZone(zone).toLocalDate();
            String contact = blank(p.getSender()) ? "Parcel customer" : p.getSender();
            csv.append(String.join(",",
                    cell(contact),
                    cell(p.getCustomerEmail()),
                    cell(p.getTrackingNumber()),
                    cell(p.getRecipient()),
                    date.toString(),
                    date.toString(),
                    cell("Parcel delivery " + p.getTrackingNumber()
                            + (blank(p.getDestination()) ? "" : " to " + p.getDestination())),
                    "1",
                    BigDecimal.valueOf(p.getEstimatedPrice()).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    cell(accountCode),
                    cell(taxType))).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"xero-invoices-" + from + "-to-" + to + ".csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }

    // Quotes values and neutralises spreadsheet formula injection.
    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replaceAll("[\\r\\n]+", " ");
        if (!v.isEmpty() && "=+-@\t".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
