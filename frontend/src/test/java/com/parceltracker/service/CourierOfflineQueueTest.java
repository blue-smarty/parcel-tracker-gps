package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
