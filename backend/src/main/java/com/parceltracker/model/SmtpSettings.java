package com.parceltracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "smtp_settings")
public class SmtpSettings {
    public static final String SINGLETON_ID = "default";

    @Id
    private String id = SINGLETON_ID;
    @Column(length = 253, nullable = false)
    private String host;
    private int port;
    private String username;
    private String fromAddress;
    private String security;
    private boolean authentication;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
    public String getSecurity() { return security; }
    public void setSecurity(String security) { this.security = security; }
    public boolean isAuthentication() { return authentication; }
    public void setAuthentication(boolean authentication) { this.authentication = authentication; }
}
