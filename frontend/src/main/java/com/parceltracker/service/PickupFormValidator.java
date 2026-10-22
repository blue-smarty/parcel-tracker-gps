package com.parceltracker.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class PickupFormValidator {
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 ()\\-]{6,19}$");

    public record Form(String contactName, String contactPhone, String street, String city, String postalCode,
                       String parcelDescription, String weight, LocalDate pickupDate, String timeWindow) { }

    private PickupFormValidator() { }

    public static Parsed parseWeight(String text) {
        try {
            double w = Double.parseDouble(text == null ? "" : text.trim().replace(',', '.'));
            return new Parsed(w);
        } catch (NumberFormatException e) {
            return new Parsed(Double.NaN);
        }
    }

    public record Parsed(double value) {
        public boolean valid() { return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0; }
    }

    /** Returns field name to error message; empty when the form is valid. */
    public static Map<String, String> validate(Form f, LocalDate today) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (blank(f.contactName())) errors.put("contactName", "Contact name is required");
        if (blank(f.contactPhone())) errors.put("contactPhone", "Contact phone is required");
        else if (!PHONE.matcher(f.contactPhone().trim()).matches()) errors.put("contactPhone", "Enter a valid phone number");
        if (blank(f.street())) errors.put("street", "Street is required");
        if (blank(f.city())) errors.put("city", "City is required");
        if (blank(f.postalCode())) errors.put("postalCode", "Postal code is required");
        if (blank(f.parcelDescription())) errors.put("parcelDescription", "Parcel description is required");
        if (blank(f.weight())) errors.put("weightKg", "Weight is required");
        else if (!parseWeight(f.weight()).valid()) errors.put("weightKg", "Weight must be a number greater than zero");
        if (f.pickupDate() == null) errors.put("pickupDate", "Pickup date is required");
        else if (f.pickupDate().isBefore(today)) errors.put("pickupDate", "Pickup date must not be in the past");
        if (blank(f.timeWindow())) errors.put("timeWindow", "Select a time window");
        return errors;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
