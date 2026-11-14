package com.parceltracker.repository;

import com.parceltracker.model.CustomerAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, String> {
    List<CustomerAddress> findAllByCustomerEmailOrderByLabelAsc(String customerEmail);

    Optional<CustomerAddress> findByIdAndCustomerEmail(String id, String customerEmail);
}
