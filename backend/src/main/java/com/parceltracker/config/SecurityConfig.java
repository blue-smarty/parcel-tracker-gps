package com.parceltracker.config;

import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.CustomerAccountRepository;
import com.parceltracker.repository.DispatcherAccountRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
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
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, error) -> {
                    if (error instanceof CsrfException) {
                        response.setStatus(403);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"code\":\"CSRF_INVALID\",\"error\":\"Security token expired. Please retry.\"}");
                    } else {
                        new AccessDeniedHandlerImpl().handle(request, response, error);
                    }
                }))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/dispatch/customers/**").hasAnyRole("ADMIN", "DISPATCHER")
                        .requestMatchers("/api/addresses/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/dispatch/couriers").hasAnyRole("ADMIN", "DISPATCHER")
                        .requestMatchers("/api/couriers/**").hasRole("COURIER")
                        .requestMatchers("/", "/index.html", "/login.html", "/forgot-password.html",
                                "/customer.html", "/admin.html", "/dispatch.html", "/admin-adhoc.html", "/admin-settings.html",
                                "/delivery-coverage.html", "/courier.html", "/courier.js",
                                "/book.html",
                                "/styles.css", "/common.js", "/auth.js", "/password-reset.js", "/quote.js",
                                "/customer.js", "/admin.js", "/admin-adhoc.js", "/admin-settings.js",
                                "/admin-coverage.js", "/book.js")
                                .permitAll()
                        .requestMatchers("/api/branding", "/api/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pricing").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/booking-route").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/address-suggestions").hasAnyRole("ADMIN", "CUSTOMER", "DISPATCHER")
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/password-reset",
                                "/api/auth/password-reset/confirm").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers("/ws/tracking/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/parcels").hasAnyRole("ADMIN", "DISPATCHER")
                        .requestMatchers(HttpMethod.GET, "/api/parcels/mine").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/parcels/*/history").hasAnyRole("ADMIN", "CUSTOMER", "DISPATCHER")
                        .requestMatchers(HttpMethod.GET, "/api/parcels/*").hasAnyRole("ADMIN", "CUSTOMER", "DISPATCHER")
                        .requestMatchers(HttpMethod.POST, "/api/parcels").hasAnyRole("ADMIN", "CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/parcels/adhoc").hasAnyRole("ADMIN", "DISPATCHER")
                        .requestMatchers(HttpMethod.POST, "/api/parcels/*/location").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/parcels/**").hasAnyRole("ADMIN", "DISPATCHER")
                        .requestMatchers(HttpMethod.DELETE, "/api/parcels/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/pickups").hasAnyRole("ADMIN", "CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/pickups/*").hasAnyRole("ADMIN", "CUSTOMER")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(
            CourierAccountRepository accounts,
            ObjectProvider<CustomerAccountRepository> customerAccounts,
            ObjectProvider<DispatcherAccountRepository> dispatcherAccounts,
            PasswordEncoder passwordEncoder,
            @Value("${parcel.auth.admin-username:}") String adminUsername,
            @Value("${parcel.auth.admin-password:}") String adminPassword) {
        if (adminUsername.isBlank() != adminPassword.isBlank()) {
            throw new IllegalStateException("Configure both admin username and password");
        }
        if (!adminPassword.isBlank() && adminPassword.length() < 12) {
            throw new IllegalStateException("Admin password must contain at least 12 characters");
        }
        // Spring Security erases credentials on the returned user after authentication,
        // so build a fresh admin user for every lookup.
        String adminHash = adminPassword.isBlank() ? null : passwordEncoder.encode(adminPassword);
        return username -> {
            if (adminHash != null && adminUsername.equals(username)) {
                return User.withUsername(adminUsername).password(adminHash).roles("ADMIN").build();
            }
            DispatcherAccountRepository dispatchers = dispatcherAccounts.getIfAvailable();
            if (dispatchers != null) {
                var dispatcher = dispatchers.findById(username);
                if (dispatcher.isPresent()) {
                    return User.withUsername(dispatcher.get().getUsername())
                            .password(dispatcher.get().getPasswordHash()).roles("DISPATCHER").build();
                }
            }
            return accounts.findById(username)
                    .map(account -> User.withUsername(account.getCourierId())
                            .password(account.getPasswordHash())
                            .roles("COURIER")
                            .build())
                    .or(() -> {
                        CustomerAccountRepository customers = customerAccounts.getIfAvailable();
                        return customers == null ? java.util.Optional.empty()
                                : customers.findById(username.toLowerCase(java.util.Locale.ROOT))
                                        .map(account -> User.withUsername(account.getEmail())
                                                .password(account.getPasswordHash())
                                                .roles("CUSTOMER")
                                                .build());
                    })
                    .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
