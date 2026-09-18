package com.travel_system.backend_app.model.dtos;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record StudentTokensDTO(UUID id, Set<String> tokens) {
}
