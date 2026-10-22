package com.parceltracker.scheduler;

import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.repository.ParcelRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Flags in-transit parcels whose courier stopped posting locations; clears the flag on recovery. */
@Component
@ConditionalOnProperty(prefix = "parcel.scheduler", name = {"enabled", "location-inactivity.enabled"},
        havingValue = "true", matchIfMissing = true)
public class CourierLocationInactivityJob {
    private static final Logger log = LoggerFactory.getLogger(CourierLocationInactivityJob.class);
    static final List<ParcelStatus> ACTIVE_STATUSES =
            List.of(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);

    private final ParcelRepository repository;
    private final SchedulerProperties properties;
    private final Clock clock;

    public CourierLocationInactivityJob(ParcelRepository repository, SchedulerProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${parcel.scheduler.location-inactivity.cron:0 */5 * * * *}")
    public void run() {
        execute();
    }

    int execute() {
        Instant cutoff = clock.instant().minus(properties.getLocationInactivity().getThreshold());
        return JobRunner.run(log, "courier-location-inactivity",
                repository.findByStatusIn(ACTIVE_STATUSES), parcel -> evaluate(parcel, cutoff));
    }

    private boolean evaluate(Parcel parcel, Instant cutoff) {
        Instant lastSeen = lastActivity(parcel);
        if (lastSeen == null || !ACTIVE_STATUSES.contains(parcel.getStatus())) {
            return false;
        }
        boolean inactive = !lastSeen.isAfter(cutoff);
        if (inactive == parcel.isLocationInactive()) {
            return false;
        }
        parcel.setLocationInactive(inactive);
        repository.save(parcel);
        if (inactive) {
            log.warn("job=courier-location-inactivity event=flagged parcel={} courier={} lastSeen={}",
                    parcel.getId(), parcel.getCourierId(), lastSeen);
        }
        return true;
    }

    private static Instant lastActivity(Parcel parcel) {
        if (parcel.getCurrentLocation() != null && parcel.getCurrentLocation().getTimestamp() != null) {
            return parcel.getCurrentLocation().getTimestamp();
        }
        return parcel.getUpdatedAt() != null ? parcel.getUpdatedAt() : parcel.getCreatedAt();
    }
}
