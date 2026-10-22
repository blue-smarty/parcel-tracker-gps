package com.parceltracker.scheduler;

import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.repository.ParcelRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Escalates parcels assigned to a courier (status CREATED + courierId) but not picked up in time. */
@Component
@ConditionalOnProperty(prefix = "parcel.scheduler", name = {"enabled", "stale-assigned.enabled"},
        havingValue = "true", matchIfMissing = true)
public class StaleAssignedEscalationJob {
    private static final Logger log = LoggerFactory.getLogger(StaleAssignedEscalationJob.class);

    private final ParcelRepository repository;
    private final SchedulerProperties properties;
    private final Clock clock;

    public StaleAssignedEscalationJob(ParcelRepository repository, SchedulerProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${parcel.scheduler.stale-assigned.cron:0 */10 * * * *}")
    public void run() {
        execute();
    }

    int execute() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.getStaleAssigned().getThreshold());
        return JobRunner.run(log, "stale-assigned-escalation",
                repository.findStaleAssigned(ParcelStatus.CREATED, cutoff), parcel -> escalate(parcel, cutoff, now));
    }

    private boolean escalate(Parcel parcel, Instant cutoff, Instant now) {
        Instant reference = parcel.getUpdatedAt() != null ? parcel.getUpdatedAt() : parcel.getCreatedAt();
        if (reference == null || reference.isAfter(cutoff)
                || parcel.getStatus() != ParcelStatus.CREATED
                || parcel.getCourierId() == null || parcel.getCourierId().isBlank()
                || parcel.getEscalatedAt() != null) {
            return false;
        }
        parcel.setEscalatedAt(now);
        if (properties.getStaleAssigned().isUnassign()) {
            parcel.setCourierId(null);
        }
        parcel.setUpdatedAt(now);
        repository.save(parcel);
        return true;
    }
}
