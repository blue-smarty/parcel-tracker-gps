package com.parceltracker.repository;

import com.parceltracker.model.CustomerAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, String> {
    List<CustomerAccount> findByCreditApprovedTrueOrderByEmailAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomerAccount c where c.email = :email")
    Optional<CustomerAccount> lockByEmail(@Param("email") String email);
}
