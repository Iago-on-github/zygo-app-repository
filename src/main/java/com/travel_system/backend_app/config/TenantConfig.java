package com.travel_system.backend_app.config;

import com.travel_system.backend_app.infrastructure.TenantResolver;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class TenantConfig implements HibernatePropertiesCustomizer {

    private final TenantResolver tenantResolver;

    public TenantConfig(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        // registra o bean no motor interno de persistência hibernate
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
    }
}
