package fr.stockshop.stock_api.user.dto;

import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String avatarUrl,
    String theme,
    int expirationAlertDays) {}
