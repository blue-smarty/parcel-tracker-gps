package com.parceltracker.controller;

import com.parceltracker.model.CreditApplication;
import com.parceltracker.repository.CustomerAccountRepository;
import com.parceltracker.service.CreditApplicationService;
import com.parceltracker.service.ParcelService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CreditApplicationController {
    private final CreditApplicationService service;
    private final CustomerAccountRepository customers;
    private final ParcelService parcels;

    public record InviteRequest(@NotBlank @Email @Size(max = 255) String email) {}
    public record SubmitRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
                                @NotBlank @Size(max = 255) String businessName,
                                @NotBlank @Size(max = 255) String contactName,
                                @NotBlank @Email @Size(max = 255) String contactEmail,
                                @NotBlank @Size(max = 40) String contactPhone,
                                @NotNull @DecimalMin(value = "0.01") @DecimalMax("9999999999.99")
                                @Digits(integer = 10, fraction = 2) BigDecimal requestedLimit) {}
    public record ReviewRequest(@NotNull Boolean approve) {}

    public CreditApplicationController(CreditApplicationService service, CustomerAccountRepository customers,
                                       ParcelService parcels) {
        this.service = service;
        this.customers = customers;
        this.parcels = parcels;
    }

    @PostMapping("/api/admin/credit/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditApplicationService.Invitation invite(@Valid @RequestBody InviteRequest request) {
        return service.invite(request.email());
    }

    @GetMapping("/api/admin/credit/applications")
    public List<CreditApplication> applications() {
        return service.list();
    }

    @GetMapping("/api/admin/credit/accounts")
    public List<String> accounts() {
        return service.approvedAccounts();
    }

    @PostMapping("/api/admin/credit/accounts/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@Valid @RequestBody InviteRequest request, Principal principal) {
        service.revoke(request.email(), principal.getName());
    }

    @PostMapping("/api/admin/credit/applications/{id}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request, Principal principal) {
        service.review(id, request.approve(), principal.getName());
    }

    @PostMapping("/api/credit/applications")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@Valid @RequestBody SubmitRequest request, Principal principal) {
        service.submit(principal.getName(), request.token(),
                new CreditApplicationService.Details(request.businessName(), request.contactName(),
                        request.contactEmail(), request.contactPhone(), request.requestedLimit()));
    }

    @GetMapping("/api/dispatch/parcels/{id}/account-type")
    public Map<String, String> accountType(@PathVariable String id) {
        String email = parcels.get(id).getCustomerEmail();
        if (email == null) return Map.of("accountType", "UNLINKED");
        boolean approved = customers.findById(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Linked customer account not found."))
                .isCreditApproved();
        return Map.of("accountType", approved ? "CREDIT" : "NO_CREDIT");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("error", error.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .findFirst().orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Parcel not found."));
    }
}
