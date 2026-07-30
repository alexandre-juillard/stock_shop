package fr.stockshop.stock_api.security;

import fr.stockshop.stock_api.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Détermine la langue applicable à chaque requête et la place dans {@link LocaleContextHolder},
 * utilisée ensuite pour traduire les messages de validation et d'erreur (voir {@code LocaleConfig}
 * et {@code GlobalExceptionHandler}).
 *
 * <p>Priorité : langue enregistrée sur le compte de l'utilisateur authentifié (JWT déjà validé par
 * {@link fr.stockshop.stock_api.security.jwt.JwtAuthenticationFilter}, exécuté juste avant), sinon
 * en-tête HTTP {@code Accept-Language}, sinon langue par défaut de l'application.
 */
@Component
@RequiredArgsConstructor
public class RequestLocaleFilter extends OncePerRequestFilter {

  private final LocaleResolver localeResolver;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    try {
      LocaleContextHolder.setLocale(resolveLocale(request));
      filterChain.doFilter(request, response);
    } finally {
      LocaleContextHolder.resetLocaleContext();
    }
  }

  private Locale resolveLocale(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof User user) {
      return Locale.forLanguageTag(user.getPreferredLocale());
    }
    return localeResolver.resolveLocale(request);
  }
}
