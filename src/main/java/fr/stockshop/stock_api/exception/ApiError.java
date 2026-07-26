package fr.stockshop.stock_api.exception;

import java.time.Instant;
import java.util.Map;

/** Représentation JSON uniforme d'une erreur retournée par l'API. */
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> fieldErrors) {
  // Constructeur compact : copie défensive à la création
  public ApiError {
    fieldErrors =
        fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors); // immuable + copie défensive
  }
}
