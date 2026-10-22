package com.parceltracker.scheduler;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "parcel.scheduler")
public class SchedulerProperties {
    private boolean enabled = true;
    private final StaleAssigned staleAssigned = new StaleAssigned();
    private final LocationInactivity locationInactivity = new LocationInactivity();
    private final AutoClose autoClose = new AutoClose();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public StaleAssigned getStaleAssigned() { return staleAssigned; }
    public LocationInactivity getLocationInactivity() { return locationInactivity; }
    public AutoClose getAutoClose() { return autoClose; }

    public static class StaleAssigned {
        private boolean enabled = true;
        private String cron = "0 */10 * * * *";
        private Duration threshold = Duration.ofHours(2);
        /** When true, the courier is unassigned so the parcel can be reassigned. */
        private boolean unassign = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public Duration getThreshold() { return threshold; }
        public void setThreshold(Duration threshold) { this.threshold = threshold; }
        public boolean isUnassign() { return unassign; }
        public void setUnassign(boolean unassign) { this.unassign = unassign; }
    }

    public static class LocationInactivity {
        private boolean enabled = true;
        private String cron = "0 */5 * * * *";
        private Duration threshold = Duration.ofMinutes(15);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public Duration getThreshold() { return threshold; }
        public void setThreshold(Duration threshold) { this.threshold = threshold; }
    }

    public static class AutoClose {
        private boolean enabled = true;
        private String cron = "0 0 * * * *";
        private Duration gracePeriod = Duration.ofHours(24);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public Duration getGracePeriod() { return gracePeriod; }
        public void setGracePeriod(Duration gracePeriod) { this.gracePeriod = gracePeriod; }
    }
}
