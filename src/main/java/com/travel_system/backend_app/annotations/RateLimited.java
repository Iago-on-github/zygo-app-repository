package com.travel_system.backend_app.annotations;

import com.travel_system.backend_app.model.enums.RateLimitPolicy;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented // anotação faz parte do contrato público, exibe explicitamente
public @interface RateLimited {
    RateLimitPolicy value();
}
