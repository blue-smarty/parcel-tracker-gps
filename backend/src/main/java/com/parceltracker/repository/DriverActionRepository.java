package com.parceltracker.repository;

import com.parceltracker.model.DriverAction;
import com.parceltracker.model.DriverActionType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverActionRepository extends JpaRepository<DriverAction, String> {
    Optional<DriverAction> findFirstByParcelIdAndTypeOrderByCreatedAtDesc(String parcelId, DriverActionType type);
}
