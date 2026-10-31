package com.parceltracker.repository;

import com.parceltracker.model.Pickup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupRepository extends JpaRepository<Pickup, String> {
}
