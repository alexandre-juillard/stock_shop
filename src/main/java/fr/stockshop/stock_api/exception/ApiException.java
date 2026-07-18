package fr.stockshop.stock_api.exception;

/**
 * Exception métier de base, portant le statut HTTP à retourner.
 */
public class ApiException extends RuntimeException {

	private final int status;

	public ApiException(int status, String message) {
		super(message);
		this.status = status;
	}

	public int getStatus() {
		return status;
	}
}

