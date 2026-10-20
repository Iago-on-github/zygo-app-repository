package com.travel_system.backend_app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "health.consent")
public class HealthConsentProperties {
    String termVersion;
    String purpose;
    String termUrl;

    public HealthConsentProperties(String termVersion, String purpose, String termUrl) {
        this.termVersion = termVersion;
        this.purpose = purpose;
        this.termUrl = termUrl;
    }

    public String getTermVersion() {
        return termVersion;
    }

    public void setTermVersion(String termVersion) {
        this.termVersion = termVersion;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getTermUrl() {
        return termUrl;
    }

    public void setTermUrl(String termUrl) {
        this.termUrl = termUrl;
    }
}
