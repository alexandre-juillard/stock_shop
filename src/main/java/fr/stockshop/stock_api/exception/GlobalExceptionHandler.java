package fr.stockshop.stock_api.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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

/**
 * Gestionnaire global des exceptions, garantissant un format de réponse JSON homogène et traduit.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final MessageSource messageSource;

  private String translate(String code, Object... args) {
    return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
    ApiError error =
        new ApiError(
            Instant.now(),
            ex.getStatus(),
            HttpStatus.valueOf(ex.getStatus()).getReasonPhrase(),
            translate(ex.getMessageCode(), ex.getArgs()),
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
            translate("error.validation.title"),
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
            translate("error.auth.invalidCredentials"),
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
            translate("error.auth.accountDisabled"),
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
            translate("error.accessDenied"),
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
            translate("error.notFound"),
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
            translate("error.internal"),
            request.getRequestURI(),
            Map.of());
    return ResponseEntity.internalServerError().body(error);
  }
}
