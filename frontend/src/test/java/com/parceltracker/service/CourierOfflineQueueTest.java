package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parceltracker.model.DeliveryProof;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CourierOfflineQueueTest {
    @TempDir Path tempDir;

    @Test
    void queuedProofSurvivesQueueRecreation() throws Exception {
        Path queueFile = tempDir.resolve("courier-updates.json");
        CourierOfflineQueue queue = new CourierOfflineQueue(queueFile);
        CourierOfflineQueue.CourierUpdate update = new CourierOfflineQueue.CourierUpdate(
                CourierOfflineQueue.CourierUpdate.Type.PROOF, "courier-1", "parcel-1");
        DeliveryProof proof = new DeliveryProof();
        proof.setBarcode("BAR-123");
        proof.setTipAmount(2.0);
        update.setProof(proof);

        queue.enqueue(update);

        assertEquals(1, new CourierOfflineQueue(queueFile).size());
    }

    @Test
    void preventsConflictingDuplicateDecisionsFromBlockingTheQueue() throws Exception {
        CourierOfflineQueue queue = new CourierOfflineQueue(tempDir.resolve("decisions.json"));
        CourierOfflineQueue.CourierUpdate accept = new CourierOfflineQueue.CourierUpdate(
                CourierOfflineQueue.CourierUpdate.Type.DECISION, "courier-1", "parcel-1");
        accept.setAccepted(true);
        CourierOfflineQueue.CourierUpdate reject = new CourierOfflineQueue.CourierUpdate(
                CourierOfflineQueue.CourierUpdate.Type.DECISION, "courier-1", "parcel-1");
        reject.setAccepted(false);

        assertTrue(queue.enqueue(accept));
        assertFalse(queue.enqueue(reject));
        assertEquals(1, queue.size());
    }
}
