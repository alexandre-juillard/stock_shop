package fr.stockshop.stock_api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Beans d'authentification isolés de {@link SecurityConfig} pour éviter toute dépendance circulaire
 * : {@code AuthenticationService} (utilisé par le succès de connexion OAuth2, lui-même injecté dans
 * {@link SecurityConfig}) a besoin de {@link PasswordEncoder} et {@link AuthenticationManager}.
 * L'{@link AuthenticationManager} est construit directement à partir de l'{@link
 * AuthenticationProvider} (et non via {@code AuthenticationConfiguration}), car cette dernière
 * déclenche la reconstruction de tout le graphe de sécurité, y compris {@link SecurityConfig},
 * recréant le cycle.
 */
@Configuration
@RequiredArgsConstructor
public class AuthenticationBeansConfig {

  private final UserDetailsService userDetailsService;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationProvider authenticationProvider) {
    return new ProviderManager(authenticationProvider);
  }
}
