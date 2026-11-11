package com.parceltracker.repository;

import com.parceltracker.model.ServiceAreaSettings;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ServiceAreaSettingsRepository extends JpaRepository<ServiceAreaSettings, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ServiceAreaSettings s where s.id = 'default'")
    Optional<ServiceAreaSettings> findForUpdate();
}
