package com.parceltracker.controller;

import com.parceltracker.model.CustomerAddress;
import com.parceltracker.repository.CustomerAccountRepository;
import com.parceltracker.repository.CustomerAddressRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/address-book")
public class AddressBookController {
    private final CustomerAddressRepository addresses;
    private final CustomerAccountRepository customers;

    public record AddressRequest(String label, String address) {}

    public AddressBookController(CustomerAddressRepository addresses, CustomerAccountRepository customers) {
        this.addresses = addresses;
        this.customers = customers;
    }

    @GetMapping
    public List<CustomerAddress> list(Authentication auth, @RequestParam(required = false) String customer) {
        return addresses.findByCustomerEmailOrderByLabelAsc(owner(auth, customer));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerAddress create(Authentication auth, @RequestParam(required = false) String customer,
            @RequestBody AddressRequest request) {
        String owner = owner(auth, customer);
        return addresses.save(new CustomerAddress(owner, required(request.label(), 100), required(request.address(), 255)));
    }

    @PutMapping("/{id}")
    @Transactional
    public CustomerAddress update(Authentication auth, @RequestParam(required = false) String customer,
            @PathVariable String id, @RequestBody AddressRequest request) {
        CustomerAddress entry = find(id, owner(auth, customer));
        entry.setLabel(required(request.label(), 100));
        entry.setAddress(required(request.address(), 255));
        return addresses.save(entry);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(Authentication auth, @RequestParam(required = false) String customer, @PathVariable String id) {
        addresses.delete(find(id, owner(auth, customer)));
    }

    private CustomerAddress find(String id, String owner) {
        return addresses.findByIdAndCustomerEmail(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found."));
    }

    private String owner(Authentication auth, String customer) {
        boolean isCustomer = auth.getAuthorities().stream()
                .anyMatch(role -> role.getAuthority().equals("ROLE_CUSTOMER"));
        if (isCustomer) {
            if (customer != null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return auth.getName().toLowerCase(Locale.ROOT);
        }
        if (customer == null || customer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer email is required.");
        }
        String email = customer.trim().toLowerCase(Locale.ROOT);
        if (!customers.existsById(email)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found.");
        }
        return email;
    }

    private String required(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid address book entry.");
        }
        return value.trim();
    }
}
