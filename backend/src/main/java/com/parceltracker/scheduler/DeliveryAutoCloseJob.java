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

/** Finalizes DELIVERED parcels after a grace period by stamping finalizedAt (updatedAt is preserved). */
@Component
@ConditionalOnProperty(prefix = "parcel.scheduler", name = {"enabled", "auto-close.enabled"},
        havingValue = "true", matchIfMissing = true)
public class DeliveryAutoCloseJob {
    private static final Logger log = LoggerFactory.getLogger(DeliveryAutoCloseJob.class);

    private final ParcelRepository repository;
    private final SchedulerProperties properties;
    private final Clock clock;

    public DeliveryAutoCloseJob(ParcelRepository repository, SchedulerProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${parcel.scheduler.auto-close.cron:0 0 * * * *}")
    public void run() {
        execute();
    }

    int execute() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.getAutoClose().getGracePeriod());
        return JobRunner.run(log, "delivery-auto-close",
                repository.findDeliveredBefore(ParcelStatus.DELIVERED, cutoff), parcel -> finalizeParcel(parcel, cutoff, now));
    }

    private boolean finalizeParcel(Parcel parcel, Instant cutoff, Instant now) {
        Instant reference = parcel.getUpdatedAt() != null ? parcel.getUpdatedAt() : parcel.getCreatedAt();
        if (reference == null || reference.isAfter(cutoff)
                || parcel.getStatus() != ParcelStatus.DELIVERED || parcel.getFinalizedAt() != null) {
            return false;
        }
        parcel.setFinalizedAt(now);
        repository.save(parcel);
        return true;
    }
}
