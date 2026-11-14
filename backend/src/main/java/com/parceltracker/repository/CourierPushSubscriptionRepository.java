package com.parceltracker.repository;

import com.parceltracker.model.CourierPushSubscription;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface CourierPushSubscriptionRepository extends JpaRepository<CourierPushSubscription, String> {
    List<CourierPushSubscription> findByCourierId(String courierId);
    @Transactional
    void deleteByCourierId(String courierId);
}
