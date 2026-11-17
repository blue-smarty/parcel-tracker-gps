package com.parceltracker.repository;

import com.parceltracker.model.CustomerCard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerCardRepository extends JpaRepository<CustomerCard, String> {
}
