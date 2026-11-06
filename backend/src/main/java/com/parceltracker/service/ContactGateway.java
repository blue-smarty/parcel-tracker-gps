package com.parceltracker.service;

/** Telephony/SMS provider boundary; real providers (e.g. a number-masking API) replace the simulated one. */
public interface ContactGateway {
    /** Opens a masked call session and returns the proxy number the driver dials. */
    String openMaskedCall(String parcelId, String customerContact);

    void sendText(String customerContact, String message);
}
