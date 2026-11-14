package com.parceltracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ParcelTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ParcelTrackerApplication.class, args);
    }
}
