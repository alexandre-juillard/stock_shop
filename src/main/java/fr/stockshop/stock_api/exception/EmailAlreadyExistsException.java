package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends ApiException {

	public EmailAlreadyExistsException(String email) {
		super(HttpStatus.CONFLICT.value(), "Un compte existe déjà avec l'email : " + email);
	}
}

