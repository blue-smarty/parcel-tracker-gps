package com.parceltracker.controller;

import com.parceltracker.service.ConfigurationHistoryService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ConfigurationHistoryController {
    private final ConfigurationHistoryService history;

    public ConfigurationHistoryController(ConfigurationHistoryService history) {
        this.history = history;
    }

    @GetMapping("/api/admin/configuration-history")
    public List<ConfigurationHistoryService.Entry> list() {
        return history.list();
    }

    @PostMapping("/api/admin/configuration-history/{id}/restore")
    public ConfigurationHistoryService.Entry restore(@PathVariable long id, Authentication authentication) {
        try {
            return history.restore(id, authentication.getName());
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }
}
