package com.parceltracker.repository;

import com.parceltracker.model.CourierAccount;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CourierAccountRepository extends MongoRepository<CourierAccount, String> {
}
