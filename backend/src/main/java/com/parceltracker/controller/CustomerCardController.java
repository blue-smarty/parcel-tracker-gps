package com.parceltracker.controller;

import com.parceltracker.service.CustomerCardService;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class CustomerCardController {
    private final CustomerCardService cards;

    public CustomerCardController(CustomerCardService cards) {
        this.cards = cards;
    }

    /** Full card, admin only (enforced in SecurityConfig). */
    @GetMapping("/admin/customers/{email}/card")
    public CustomerCardService.Card adminCard(@PathVariable String email) {
        requireEnabled();
        return cards.find(normalize(email)).orElseThrow(CustomerCardController::notFound);
    }

    /** Restricted card for dispatchers and admins: no email or account details. */
    @GetMapping("/dispatch/customers/{email}/card")
    public CustomerCardService.DispatcherView dispatcherCard(@PathVariable String email) {
        requireEnabled();
        return cards.findForDispatcher(normalize(email)).orElseThrow(CustomerCardController::notFound);
    }

    private void requireEnabled() {
        if (!cards.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Customer cards are not configured");
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
