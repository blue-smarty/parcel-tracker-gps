package com.parceltracker.repository;

import com.parceltracker.model.CreditApplication;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditApplicationRepository extends JpaRepository<CreditApplication, UUID> {
    Optional<CreditApplication> findByTokenHashAndCustomerEmail(String tokenHash, String customerEmail);
    List<CreditApplication> findByCustomerEmailAndStatus(String email, String status);
    List<CreditApplication> findAllByOrderByInvitedAtDesc();

    @Query("select a.customerEmail from CreditApplication a where a.id = :id")
    Optional<String> findCustomerEmailById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditApplication a where a.id = :id")
    Optional<CreditApplication> lockById(@Param("id") UUID id);
}
