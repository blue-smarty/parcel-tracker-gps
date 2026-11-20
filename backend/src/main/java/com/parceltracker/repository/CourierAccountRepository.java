package com.parceltracker.repository;

import com.parceltracker.model.CourierAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourierAccountRepository extends JpaRepository<CourierAccount, String> {
    boolean existsByCourierIdIgnoreCase(String courierId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select courier from CourierAccount courier where courier.courierId = :courierId")
    Optional<CourierAccount> findForCapacityCheck(@Param("courierId") String courierId);
}
