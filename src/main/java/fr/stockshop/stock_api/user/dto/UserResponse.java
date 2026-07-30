package fr.stockshop.stock_api.user.dto;

import fr.stockshop.stock_api.user.entity.Role;
import java.util.UUID;

public record UserResponse(
    UUID id, String email, String firstName, String lastName, Role role, String preferredLocale) {}
