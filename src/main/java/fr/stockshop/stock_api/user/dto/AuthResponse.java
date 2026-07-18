package fr.stockshop.stock_api.user.dto;

/**
 * Réponse renvoyée après une inscription, une connexion ou un rafraîchissement de token.
 */
public record AuthResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn
) {
}

