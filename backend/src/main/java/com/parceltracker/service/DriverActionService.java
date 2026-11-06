package com.parceltracker.service;

import com.parceltracker.dto.DriverActionResult;
import com.parceltracker.model.DriverAction;
import com.parceltracker.model.DriverActionType;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.repository.DriverActionRepository;
import com.parceltracker.repository.ParcelRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DriverActionService {
    static final Duration TEXT_COOLDOWN = Duration.ofMinutes(2);
    private static final Set<ParcelStatus> NEARBY_STATUSES =
            Set.of(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
    private static final Set<ParcelStatus> ACTIVE_STATUSES = Set.of(ParcelStatus.ACCEPTED, ParcelStatus.PICKED_UP,
            ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);

    private final ParcelRepository parcels;
    private final DriverActionRepository actions;
    private final ContactGateway gateway;

    public DriverActionService(ParcelRepository parcels, DriverActionRepository actions, ContactGateway gateway) {
        this.parcels = parcels;
        this.actions = actions;
        this.gateway = gateway;
    }

    public DriverActionResult maskedCall(String courierId, String parcelId) {
        Parcel parcel = activeParcel(courierId, parcelId);
        String contact = requireContact(parcel);
        String proxy = gateway.openMaskedCall(parcelId, contact);
        DriverAction saved = record(courierId, parcelId, DriverActionType.MASKED_CALL, null);
        return new DriverActionResult(DriverActionType.MASKED_CALL, "Masked call ready", proxy, saved.getCreatedAt());
    }

    public DriverActionResult proximityText(String courierId, String parcelId) {
        Parcel parcel = activeParcel(courierId, parcelId);
        if (!NEARBY_STATUSES.contains(parcel.getStatus())) {
            throw new IllegalStateException("Proximity text requires an in-transit or out-for-delivery parcel");
        }
        Optional<DriverAction> last = actions.findFirstByParcelIdAndTypeOrderByCreatedAtDesc(
                parcelId, DriverActionType.PROXIMITY_TEXT);
        if (last.isPresent() && last.get().getCreatedAt().isAfter(Instant.now().minus(TEXT_COOLDOWN))) {
            return new DriverActionResult(DriverActionType.PROXIMITY_TEXT, "Text already sent",
                    null, last.get().getCreatedAt());
        }
        gateway.sendText(requireContact(parcel),
                "Your parcel " + parcel.getTrackingNumber() + " is 3 minutes away.");
        DriverAction saved = record(courierId, parcelId, DriverActionType.PROXIMITY_TEXT, null);
        return new DriverActionResult(DriverActionType.PROXIMITY_TEXT, "Text sent", null, saved.getCreatedAt());
    }

    /** A blank code re-logs the last code recorded for this parcel so repeat taps need no typing. */
    public DriverActionResult logGateCode(String courierId, String parcelId, String code) {
        activeParcel(courierId, parcelId);
        String value = code == null || code.isBlank() ? null : code.trim();
        if (value == null) {
            value = actions.findFirstByParcelIdAndTypeOrderByCreatedAtDesc(parcelId, DriverActionType.GATE_CODE)
                    .map(DriverAction::getDetail)
                    .orElseThrow(() -> new IllegalStateException("No gate code on file for this delivery"));
        }
        DriverAction saved = record(courierId, parcelId, DriverActionType.GATE_CODE, value);
        return new DriverActionResult(DriverActionType.GATE_CODE, "Gate code logged", null, saved.getCreatedAt());
    }

    private DriverAction record(String courierId, String parcelId, DriverActionType type, String detail) {
        DriverAction action = new DriverAction();
        action.setCourierId(courierId);
        action.setParcelId(parcelId);
        action.setType(type);
        action.setDetail(detail);
        action.setCreatedAt(Instant.now());
        return actions.save(action);
    }

    private Parcel activeParcel(String courierId, String parcelId) {
        if (courierId == null || courierId.isBlank()) {
            throw new IllegalArgumentException("Courier ID is required");
        }
        Parcel parcel = parcels.findById(parcelId)
                .orElseThrow(() -> new NoSuchElementException("Parcel not found: " + parcelId));
        if (!courierId.equals(parcel.getCourierId())) {
            throw new NoSuchElementException("Parcel not assigned to courier: " + parcelId);
        }
        if (!ACTIVE_STATUSES.contains(parcel.getStatus())) {
            throw new IllegalStateException("Quick actions require an active delivery");
        }
        return parcel;
    }

    private static String requireContact(Parcel parcel) {
        if (parcel.getCustomerContact() == null || parcel.getCustomerContact().isBlank()) {
            throw new IllegalStateException("No customer contact on file for this delivery");
        }
        return parcel.getCustomerContact();
    }
}
