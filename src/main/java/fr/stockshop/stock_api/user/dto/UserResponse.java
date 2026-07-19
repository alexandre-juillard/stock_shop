package fr.stockshop.stock_api.user.dto;

import java.util.UUID;

import fr.stockshop.stock_api.user.entity.Role;

public record UserResponse(
		UUID id,
		String email,
		String firstName,
		String lastName,
		Role role
) {
}

