package com.parceltracker.repository;

import com.parceltracker.model.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    void deleteByCustomerEmail(String customerEmail);
    Optional<PasswordResetToken> findFirstByCustomerEmailOrderByCreatedAtDesc(String customerEmail);
}
