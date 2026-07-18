package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends ApiException {

	public InvalidTokenException(String message) {
		super(HttpStatus.UNAUTHORIZED.value(), message);
	}
}

