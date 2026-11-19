package com.parceltracker.repository;

import com.parceltracker.model.CreditAccountEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditAccountEventRepository extends JpaRepository<CreditAccountEvent, UUID> {}
