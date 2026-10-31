package com.parceltracker.repository;

import com.parceltracker.model.CourierAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierAccountRepository extends JpaRepository<CourierAccount, String> {
}
