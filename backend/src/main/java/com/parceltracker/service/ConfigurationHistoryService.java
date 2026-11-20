package com.parceltracker.service;

import com.parceltracker.model.CourierAccount;
import com.parceltracker.model.OperationalSettings;
import com.parceltracker.model.ServiceAreaSettings;
import com.parceltracker.model.BrandingSettings;
import com.parceltracker.model.WebsiteTheme;
import com.parceltracker.model.PricingSettings;
import com.parceltracker.model.ServiceLevelSettings;
import com.parceltracker.model.SmtpSettings;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.OperationalSettingsRepository;
import com.parceltracker.repository.PricingSettingsRepository;
import com.parceltracker.repository.ServiceLevelSettingsRepository;
import com.parceltracker.repository.SmtpSettingsRepository;
import com.parceltracker.repository.ServiceAreaSettingsRepository;
import com.parceltracker.repository.BrandingRepository;
import com.parceltracker.repository.WebsiteThemeRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConfigurationHistoryService {
    public record Entry(long id, String category, String changedBy, Instant changedAt,
            JsonNode before, JsonNode after) { }
    public record CourierCapacity(String courierId, String vehicleType, Integer maxActiveParcels,
            Double maxPayloadWeightKg) { }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PricingSettingsRepository pricing;
    private final SmtpSettingsRepository smtp;
    private final ServiceLevelSettingsRepository serviceLevels;
    private final OperationalSettingsRepository operations;
    private final CourierAccountRepository couriers;
    private final ServiceAreaSettingsRepository serviceArea;
    private final BrandingRepository branding;
    private final WebsiteThemeRepository websiteThemes;

    public ConfigurationHistoryService(JdbcTemplate jdbc, ObjectMapper mapper,
            PricingSettingsRepository pricing, SmtpSettingsRepository smtp,
            ServiceLevelSettingsRepository serviceLevels, OperationalSettingsRepository operations,
            CourierAccountRepository couriers, ServiceAreaSettingsRepository serviceArea,
            BrandingRepository branding, WebsiteThemeRepository websiteThemes) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.pricing = pricing;
        this.smtp = smtp;
        this.serviceLevels = serviceLevels;
        this.operations = operations;
        this.couriers = couriers;
        this.serviceArea = serviceArea;
        this.branding = branding;
        this.websiteThemes = websiteThemes;
    }

    public void record(String category, Object before, Object after, String changedBy) {
        try {
            String beforeJson = before == null ? null : mapper.writeValueAsString(before);
            String afterJson = mapper.writeValueAsString(after);
            if (java.util.Objects.equals(beforeJson, afterJson)) return;
            jdbc.update("INSERT INTO admin_configuration_history "
                            + "(category, changed_by, changed_at, before_state, after_state) "
                            + "VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))",
                    category, changedBy, java.sql.Timestamp.from(Instant.now()), beforeJson, afterJson);
        } catch (JacksonException error) {
            throw new IllegalStateException("Could not save configuration history.", error);
        }
    }

    public <T> T snapshot(T value, Class<T> type) {
        if (value == null) return null;
        try {
            return mapper.treeToValue(mapper.valueToTree(value), type);
        } catch (JacksonException error) {
            throw new IllegalStateException("Could not snapshot configuration for history.", error);
        }
    }

    public List<Entry> list() {
        return jdbc.query("SELECT id, category, changed_by, changed_at, before_state, after_state "
                        + "FROM admin_configuration_history ORDER BY changed_at DESC, id DESC LIMIT 100",
                this::mapEntry);
    }

    @Transactional
    public Entry restore(long id, String changedBy) {
        Entry entry = jdbc.query("SELECT id, category, changed_by, changed_at, before_state, after_state "
                        + "FROM admin_configuration_history WHERE id = ? FOR UPDATE",
                this::mapEntry, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Configuration history entry not found."));
        JsonNode target = entry.before();
        if (target == null || target.isNull()) {
            switch (entry.category()) {
                case "PRICING" -> pricing.deleteById(PricingSettings.SINGLETON_ID);
                case "SMTP" -> smtp.deleteById(SmtpSettings.SINGLETON_ID);
                case "SERVICE_LEVELS" -> serviceLevels.deleteById(ServiceLevelSettings.SINGLETON_ID);
                case "OPERATIONAL_LIMITS" -> operations.deleteById(OperationalSettings.SINGLETON_ID);
                case "SERVICE_AREA" -> serviceArea.deleteById(ServiceAreaSettings.SINGLETON_ID);
                case "BRANDING" -> branding.deleteById(BrandingSettings.SINGLETON_ID);
                case "WEBSITE_THEME" -> websiteThemes.deleteById("default");
                default -> throw new IllegalArgumentException("This configuration category cannot be restored.");
            }
        } else {
            switch (entry.category()) {
                case "PRICING" -> {
                    PricingSettings current = pricing.findById(PricingSettings.SINGLETON_ID).orElse(null);
                    PricingSettings restored = mapper.treeToValue(target, PricingSettings.class);
                    record(entry.category(), current, restored, changedBy);
                    pricing.save(restored);
                    return latest();
                }
                case "SMTP" -> {
                    SmtpSettings current = smtp.findById(SmtpSettings.SINGLETON_ID).orElse(null);
                    SmtpSettings restored = mapper.treeToValue(target, SmtpSettings.class);
                    record(entry.category(), current, restored, changedBy);
                    smtp.save(restored);
                    return latest();
                }
                case "SERVICE_LEVELS" -> {
                    ServiceLevelSettings current = serviceLevels.findById(ServiceLevelSettings.SINGLETON_ID).orElse(null);
                    ServiceLevelSettings restored = mapper.treeToValue(target, ServiceLevelSettings.class);
                    record(entry.category(), current, restored, changedBy);
                    serviceLevels.save(restored);
                    return latest();
                }
                case "OPERATIONAL_LIMITS" -> {
                    OperationalSettings current = operations.findById(OperationalSettings.SINGLETON_ID).orElse(null);
                    OperationalSettings restored = mapper.treeToValue(target, OperationalSettings.class);
                    OperationalSettingsService.validateSettings(restored);
                    record(entry.category(), current, restored, changedBy);
                    operations.save(restored);
                    return latest();
                }
                case "SERVICE_AREA" -> {
                    ServiceAreaSettings current = serviceArea.findById(ServiceAreaSettings.SINGLETON_ID).orElse(null);
                    ServiceAreaSettings restored = mapper.treeToValue(target, ServiceAreaSettings.class);
                    record(entry.category(), current, restored, changedBy);
                    serviceArea.save(restored);
                    return latest();
                }
                case "BRANDING" -> {
                    BrandingSettings current = branding.findById(BrandingSettings.SINGLETON_ID).orElse(null);
                    BrandingSettings restored = mapper.treeToValue(target, BrandingSettings.class);
                    record(entry.category(), current, restored, changedBy);
                    branding.save(restored);
                    return latest();
                }
                case "WEBSITE_THEME" -> {
                    WebsiteTheme current = websiteThemes.findById("default").orElse(null);
                    WebsiteTheme restored = mapper.treeToValue(target, WebsiteTheme.class);
                    record(entry.category(), current, restored, changedBy);
                    websiteThemes.save(restored);
                    return latest();
                }
                case "COURIER_CAPACITY" -> {
                    CourierCapacity capacity = mapper.treeToValue(target, CourierCapacity.class);
                    CourierAccount courier = couriers.findById(capacity.courierId())
                            .orElseThrow(() -> new IllegalArgumentException("Courier no longer exists."));
                    CourierCapacity current = capacityOf(courier);
                    apply(courier, capacity);
                    record(entry.category(), current, capacity, changedBy);
                    couriers.save(courier);
                    return latest();
                }
                default -> throw new IllegalArgumentException("This configuration category cannot be restored.");
            }
        }
        record(entry.category(), entry.after(), target, changedBy);
        return latest();
    }

    private Entry latest() {
        return jdbc.query("SELECT id, category, changed_by, changed_at, before_state, after_state "
                        + "FROM admin_configuration_history ORDER BY changed_at DESC, id DESC LIMIT 1",
                this::mapEntry).get(0);
    }

    private Entry mapEntry(ResultSet row, int index) throws SQLException {
        try {
            Object before = row.getObject("before_state");
            Object after = row.getObject("after_state");
            return new Entry(row.getLong("id"), row.getString("category"), row.getString("changed_by"),
                    row.getTimestamp("changed_at").toInstant(),
                    before == null ? null : mapper.readTree(before.toString()),
                    after == null ? null : mapper.readTree(after.toString()));
        } catch (JacksonException error) {
            throw new SQLException("Could not read configuration history.", error);
        }
    }

    private static CourierCapacity capacityOf(CourierAccount courier) {
        return new CourierCapacity(courier.getCourierId(), courier.getVehicleType(),
                courier.getMaxActiveParcels(), courier.getMaxPayloadWeightKg());
    }

    private static void apply(CourierAccount courier, CourierCapacity capacity) {
        courier.setVehicleType(capacity.vehicleType());
        courier.setMaxActiveParcels(capacity.maxActiveParcels());
        courier.setMaxPayloadWeightKg(capacity.maxPayloadWeightKg());
    }
}
