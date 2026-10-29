package com.parceltracker.config;

import com.parceltracker.repository.CourierAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/parcels/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/parcels/**").hasRole("ADMIN")
                        .requestMatchers("/api/couriers/**").hasRole("COURIER")
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(
            CourierAccountRepository accounts,
            PasswordEncoder passwordEncoder,
            @Value("${parcel.auth.admin-username:}") String adminUsername,
            @Value("${parcel.auth.admin-password:}") String adminPassword) {
        if (adminUsername.isBlank() != adminPassword.isBlank()) {
            throw new IllegalStateException("Configure both admin username and password");
        }
        if (!adminPassword.isBlank() && adminPassword.length() < 12) {
            throw new IllegalStateException("Admin password must contain at least 12 characters");
        }
        var admin = adminPassword.isBlank() ? null : User.withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN")
                .build();
        return username -> {
            if (admin != null && admin.getUsername().equals(username)) return admin;
            return accounts.findById(username)
                    .map(account -> User.withUsername(account.getCourierId())
                            .password(account.getPasswordHash())
                            .roles("COURIER")
                            .build())
                    .orElseThrow(() -> new UsernameNotFoundException("Courier account not found"));
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
