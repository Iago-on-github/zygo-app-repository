package com.travel_system.backend_app.annotations;

import com.travel_system.backend_app.model.enums.AccessCategory;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAccess {
    AccessCategory value();
}
