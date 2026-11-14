package com.parceltracker.model;

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

    @Column(nullable = false)
    private String label;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    @Column(nullable = false)
    private String address;

    public CustomerAddress() {
    }

    public CustomerAddress(String customerEmail, String label, String contactName,
            String contactPhone, String address) {
        this.customerEmail = customerEmail;
        this.label = label;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.address = address;
    }

    public String getId() {
        return id;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getLabel() {
        return label;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getAddress() {
        return address;
    }

    public void update(String label, String contactName, String contactPhone, String address) {
        this.label = label;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.address = address;
    }
}
