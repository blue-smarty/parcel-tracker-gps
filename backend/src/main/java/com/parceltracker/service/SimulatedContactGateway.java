package com.parceltracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(value = ContactGateway.class, ignored = SimulatedContactGateway.class)
public class SimulatedContactGateway implements ContactGateway {
    private static final Logger log = LoggerFactory.getLogger(SimulatedContactGateway.class);

    @Override
    public String openMaskedCall(String parcelId, String customerContact) {
        log.info("Simulated masked call opened for parcel {}", parcelId);
        return "+1-555-0100";
    }

    @Override
    public void sendText(String customerContact, String message) {
        log.info("Simulated text sent: {}", message);
    }
}
