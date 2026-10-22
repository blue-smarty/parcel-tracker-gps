package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;

import com.parceltracker.service.PickupFormValidator.Form;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PickupFormValidatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 10);

    private Form form(String phone, String weight, LocalDate date) {
        return new Form("Ann", phone, "1 Main St", "Sydney", "2000", "Books", weight, date, "09:00-12:00");
    }

    @Test
    void validFormHasNoErrors() {
        assertTrue(PickupFormValidator.validate(form("+61 400 000 000", "2.5", TODAY), TODAY).isEmpty());
    }

    @Test
    void reportsFieldErrors() {
        var errors = PickupFormValidator.validate(form("abc", "0", TODAY.minusDays(1)), TODAY);
        assertTrue(errors.containsKey("contactPhone"));
        assertTrue(errors.containsKey("weightKg"));
        assertTrue(errors.containsKey("pickupDate"));
    }

    @Test
    void requiredFieldsMissing() {
        var errors = PickupFormValidator.validate(new Form("", null, " ", "", "", "", "", null, null), TODAY);
        assertEquals(9, errors.size());
    }

    @Test
    void weightAcceptsCommaAndRejectsText() {
        assertEquals(1.5, PickupFormValidator.parseWeight("1,5").value());
        assertFalse(PickupFormValidator.parseWeight("x").valid());
        assertFalse(PickupFormValidator.parseWeight("-1").valid());
    }
}
