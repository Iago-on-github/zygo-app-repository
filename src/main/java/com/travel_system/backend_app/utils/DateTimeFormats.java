package com.travel_system.backend_app.utils;

import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class DateTimeFormats {
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    public String formatInstantDate(Instant date) {
        return formatter.format(date);
    }

    public String localDateFormat(LocalDate date) {
        return formatter.format(date);
    }
}
