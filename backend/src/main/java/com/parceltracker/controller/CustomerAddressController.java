package com.parceltracker.controller;

import com.parceltracker.dto.CustomerAddressRequest;
import com.parceltracker.model.CustomerAddress;
import com.parceltracker.repository.CustomerAddressRepository;
import com.parceltracker.repository.CustomerAccountRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CustomerAddressController {
    private final CustomerAddressRepository addresses;
    private final CustomerAccountRepository customers;

    public CustomerAddressController(CustomerAddressRepository addresses, CustomerAccountRepository customers) {
        this.addresses = addresses;
        this.customers = customers;
    }

    @GetMapping("/addresses")
    public List<CustomerAddress> mine(Authentication authentication) {
        return addresses.findAllByCustomerEmailOrderByLabelAsc(authentication.getName());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerAddress addMine(@Valid @RequestBody CustomerAddressRequest request, Authentication authentication) {
        return add(authentication.getName(), request);
    }

    @PutMapping("/addresses/{id}")
    public CustomerAddress updateMine(@PathVariable String id, @Valid @RequestBody CustomerAddressRequest request,
            Authentication authentication) {
        return update(id, authentication.getName(), request);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMine(@PathVariable String id, Authentication authentication) {
        delete(id, authentication.getName());
    }

    @GetMapping("/dispatch/customers/{email}/addresses")
    public List<CustomerAddress> forCustomer(@PathVariable String email) {
        String customerEmail = customerEmail(email);
        requireCustomer(customerEmail);
        return addresses.findAllByCustomerEmailOrderByLabelAsc(customerEmail);
    }

    @PostMapping("/dispatch/customers/{email}/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerAddress addForCustomer(@PathVariable String email,
            @Valid @RequestBody CustomerAddressRequest request) {
        String customerEmail = customerEmail(email);
        requireCustomer(customerEmail);
        return add(customerEmail, request);
    }

    @PutMapping("/dispatch/customers/{email}/addresses/{id}")
    public CustomerAddress updateForCustomer(@PathVariable String email, @PathVariable String id,
            @Valid @RequestBody CustomerAddressRequest request) {
        String customerEmail = customerEmail(email);
        requireCustomer(customerEmail);
        return update(id, customerEmail, request);
    }

    @DeleteMapping("/dispatch/customers/{email}/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteForCustomer(@PathVariable String email, @PathVariable String id) {
        String customerEmail = customerEmail(email);
        requireCustomer(customerEmail);
        delete(id, customerEmail);
    }

    private CustomerAddress add(String email, CustomerAddressRequest request) {
        return addresses.save(new CustomerAddress(email, clean(request.label()), clean(request.contactName()),
                clean(request.contactPhone()), clean(request.address())));
    }

    private CustomerAddress update(String id, String email, CustomerAddressRequest request) {
        CustomerAddress address = findOwned(id, email);
        address.update(clean(request.label()), clean(request.contactName()),
                clean(request.contactPhone()), clean(request.address()));
        return addresses.save(address);
    }

    private void delete(String id, String email) {
        addresses.delete(findOwned(id, email));
    }

    private CustomerAddress findOwned(String id, String email) {
        return addresses.findByIdAndCustomerEmail(id, email)
                .orElseThrow(() -> new NoSuchElementException("Address not found"));
    }

    private void requireCustomer(String email) {
        if (!customers.existsById(email)) {
            throw new NoSuchElementException("Customer account not found");
        }
    }

    private static String customerEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value.trim();
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", error.getMessage()));
    }
}
