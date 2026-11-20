package com.parceltracker.service;

import com.parceltracker.model.OperationalSettings;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelItem;
import com.parceltracker.repository.OperationalSettingsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class OperationalSettingsService {
    private static final Pattern WINDOW = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d-([01]\\d|2[0-3]):[0-5]\\d$");
    private final OperationalSettingsRepository repository;

    public OperationalSettingsService(OperationalSettingsRepository repository) {
        this.repository = repository;
    }

    public OperationalSettings get() {
        return repository.findById(OperationalSettings.SINGLETON_ID).orElseGet(OperationalSettings::defaults);
    }

    public OperationalSettings save(OperationalSettings settings) {
        return repository.save(settings);
    }

    public void validateParcel(Parcel parcel) {
        OperationalSettings settings = get();
        List<ParcelItem> items = parcel.getItems();
        if (items != null && !items.isEmpty()) {
            if (items.size() > settings.getMaxPackageLines()) {
                throw new IllegalArgumentException("This booking exceeds the maximum number of package lines.");
            }
            int totalQuantity = 0;
            for (ParcelItem item : items) {
                totalQuantity += item.quantity();
                if (item.quantity() > settings.getMaxQuantityPerItem()
                        || item.weight() > settings.getMaxItemWeightKg()
                        || item.length() > settings.getMaxDimensionCm()
                        || item.width() > settings.getMaxDimensionCm()
                        || item.height() > settings.getMaxDimensionCm()) {
                    throw new IllegalArgumentException("An item exceeds the configured quantity, weight, or dimension limit.");
                }
            }
            if (totalQuantity > settings.getMaxItemsPerBooking()) {
                throw new IllegalArgumentException("This booking exceeds the maximum number of items.");
            }
        } else if (parcel.getWeightKg() != null && parcel.getWeightKg() > settings.getMaxItemWeightKg()) {
            throw new IllegalArgumentException("The parcel exceeds the configured weight limit.");
        }
        if ((items == null || items.isEmpty()) && (parcel.getWidthCm() != null
                && parcel.getWidthCm() > settings.getMaxDimensionCm()
                || parcel.getLengthCm() != null && parcel.getLengthCm() > settings.getMaxDimensionCm()
                || parcel.getHeightCm() != null && parcel.getHeightCm() > settings.getMaxDimensionCm())) {
            throw new IllegalArgumentException("The parcel exceeds the configured dimension limit.");
        }
        boolean hasDate = parcel.getScheduledDate() != null;
        boolean hasWindow = parcel.getScheduledWindow() != null && !parcel.getScheduledWindow().isBlank();
        if (hasDate != hasWindow) {
            throw new IllegalArgumentException("Choose both a delivery date and delivery window.");
        }
        if (hasDate) validateSchedule(settings, parcel.getScheduledDate(), parcel.getScheduledWindow(), Instant.now());
    }

    public void validateSchedule(OperationalSettings settings, LocalDate date, String window, Instant now) {
        if (date == null || window == null || !settings.getDeliveryWindows().contains(window)) {
            throw new IllegalArgumentException("Choose an available delivery window.");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(settings.getTimeZone());
        } catch (RuntimeException error) {
            throw new IllegalStateException("Configured booking timezone is invalid.", error);
        }
        LocalDate today = now.atZone(zone).toLocalDate();
        if (date.isBefore(today) || date.isAfter(today.plusDays(settings.getMaxScheduleDaysAhead()))) {
            throw new IllegalArgumentException("Choose a date within the available booking range.");
        }
        if (date.equals(today) && now.atZone(zone).toLocalTime().isAfter(settings.getBookingCutoff())) {
            throw new IllegalArgumentException("Today's booking cutoff has passed; choose a later date.");
        }
        if (date.equals(today)) {
            int start = Integer.parseInt(window.substring(0, 2)) * 60 + Integer.parseInt(window.substring(3, 5));
            int current = now.atZone(zone).getHour() * 60 + now.atZone(zone).getMinute();
            if (start <= current) throw new IllegalArgumentException("That delivery window has already started; choose a later window.");
        }
    }

    public static void validateSettings(OperationalSettings settings) {
        if (settings.getMaxItemsPerBooking() < 1 || settings.getMaxItemsPerBooking() > 1000
                || settings.getMaxPackageLines() < 1 || settings.getMaxPackageLines() > 100
                || settings.getMaxQuantityPerItem() < 1 || settings.getMaxQuantityPerItem() > 1000
                || !positive(settings.getMaxItemWeightKg()) || settings.getMaxItemWeightKg() > 1_000_000
                || !positive(settings.getMaxDimensionCm()) || settings.getMaxDimensionCm() > 1_000_000
                || settings.getBookingCutoff() == null || settings.getMaxScheduleDaysAhead() < 1
                || settings.getMaxScheduleDaysAhead() > 365 || settings.getStaleAssignmentMinutes() < 1
                || settings.getStaleAssignmentMinutes() > 10080) {
            throw new IllegalArgumentException("Operational limits are outside their allowed ranges.");
        }
        try {
            ZoneId.of(settings.getTimeZone());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Choose a valid timezone.", error);
        }
        List<String> windows = settings.getDeliveryWindows();
        if (windows == null || windows.isEmpty() || windows.size() > 20 || new HashSet<>(windows).size() != windows.size()) {
            throw new IllegalArgumentException("Configure 1-20 unique delivery windows.");
        }
        int previousEnd = -1;
        for (String window : windows) {
            var matcher = window == null ? null : WINDOW.matcher(window);
            if (matcher == null || !matcher.matches()) {
                throw new IllegalArgumentException("Delivery windows must use HH:mm-HH:mm format.");
            }
            int start = Integer.parseInt(window.substring(0, 2)) * 60 + Integer.parseInt(window.substring(3, 5));
            int end = Integer.parseInt(window.substring(6, 8)) * 60 + Integer.parseInt(window.substring(9, 11));
            if (start >= end || start < previousEnd) {
                throw new IllegalArgumentException("Delivery windows must be ordered and cannot overlap.");
            }
            previousEnd = end;
        }
    }

    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
}
