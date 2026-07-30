package fr.stockshop.stock_api.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Gestionnaire global des exceptions, garantissant un format de réponse JSON homogène. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
    ApiError error =
        new ApiError(
            Instant.now(),
            ex.getStatus(),
            HttpStatus.valueOf(ex.getStatus()).getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI(),
            Map.of());
    return ResponseEntity.status(ex.getStatus()).body(error);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> fieldErrors = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
    ApiError error =
        new ApiError(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "Erreur de validation des données",
            request.getRequestURI(),
            fieldErrors);
    return ResponseEntity.badRequest().body(error);
  }

  @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
  public ResponseEntity<ApiError> handleAuthenticationException(
      RuntimeException ex, HttpServletRequest request) {
    ApiError error =
        new ApiError(
            Instant.now(),
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            "Email ou mot de passe incorrect",
            request.getRequestURI(),
            Map.of());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
  }

  @ExceptionHandler(DisabledException.class)
  public ResponseEntity<ApiError> handleDisabledAccount(
      DisabledException ex, HttpServletRequest request) {
    ApiError error =
        new ApiError(
            Instant.now(),
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            "Le compte n'a pas encore été confirmé",
            request.getRequestURI(),
            Map.of());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDeniedException(
      AccessDeniedException ex, HttpServletRequest request) {
    ApiError error =
        new ApiError(
            Instant.now(),
            HttpStatus.FORBIDDEN.value(),
            "Forbidden",
            "Accès refusé",
            request.getRequestURI(),
            Map.of());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
  }

  @ExceptionHandler({NoResourceFoundException.class, HttpMediaTypeNotSupportedException.class})
  public ResponseEntity<ApiError> handleNotFoundOrUnsupportedMediaType(
      Exception ex, HttpServletRequest request) {
    ApiError error =
        new ApiError(
            Instant.now(),
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.getMessage(),
            request.getRequestURI(),
            Map.of());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
    ApiError error =
        new ApiError(
            Instant.now(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "Une erreur inattendue est survenue",
            request.getRequestURI(),
            Map.of());
    return ResponseEntity.internalServerError().body(error);
  }
}
