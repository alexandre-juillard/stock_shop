package fr.stockshop.stock_api.user.dto;

import jakarta.validation.constraints.Email;

public record UpdateProfileRequest(
    String firstName,
    String lastName,
    @Email(message = "{validation.email.invalid}") String email) {}
