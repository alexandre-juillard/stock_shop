package fr.stockshop.stock_api.user.dto;

import fr.stockshop.stock_api.user.entity.Role;

public record UserResponse(
		Long id,
		String email,
		String firstName,
		String lastName,
		Role role
) {
}

