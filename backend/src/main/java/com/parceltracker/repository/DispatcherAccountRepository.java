package com.parceltracker.repository;

import com.parceltracker.model.DispatcherAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatcherAccountRepository extends JpaRepository<DispatcherAccount, String> {
}
