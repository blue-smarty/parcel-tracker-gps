package com.parceltracker.scheduler;

import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;

/** Runs a job over candidates with per-item error isolation and structured logging. */
final class JobRunner {
    private JobRunner() {
    }

    /** The processor returns true when it changed the item. Returns the processed count. */
    static <T> int run(Logger log, String job, List<T> candidates, Predicate<T> processor) {
        log.info("job={} event=start candidates={}", job, candidates.size());
        int processed = 0;
        int errors = 0;
        for (T item : candidates) {
            try {
                if (processor.test(item)) {
                    processed++;
                }
            } catch (RuntimeException e) {
                errors++;
                log.error("job={} event=item-error error={}", job, e.toString(), e);
            }
        }
        log.info("job={} event=end candidates={} processed={} errors={}", job, candidates.size(), processed, errors);
        return processed;
    }
}
