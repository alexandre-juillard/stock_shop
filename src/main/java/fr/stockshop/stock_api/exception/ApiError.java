package fr.stockshop.stock_api.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Représentation JSON uniforme d'une erreur retournée par l'API.
 */
public record ApiError(
		Instant timestamp,
		int status,
		String error,
		String message,
		String path,
		Map<String, String> fieldErrors
) {
	public ApiError(int status, String error, String message, String path) {
		this(Instant.now(), status, error, message, path, null);
	}

	public ApiError(int status, String error, String message, String path, Map<String, String> fieldErrors) {
		this(Instant.now(), status, error, message, path, fieldErrors);
	}
}

