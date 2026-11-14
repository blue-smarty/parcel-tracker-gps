package com.parceltracker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_addresses")
public class CustomerAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private String address;

    public CustomerAddress() {}

    public CustomerAddress(String customerEmail, String label, String address) {
        this.customerEmail = customerEmail;
        this.label = label;
        this.address = address;
    }

    public String getId() { return id; }
    @JsonIgnore
    public String getCustomerEmail() { return customerEmail; }
    public String getLabel() { return label; }
    public String getAddress() { return address; }
    public void setLabel(String label) { this.label = label; }
    public void setAddress(String address) { this.address = address; }
}
