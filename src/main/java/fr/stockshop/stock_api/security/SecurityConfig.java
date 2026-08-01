package fr.stockshop.stock_api.security;

import fr.stockshop.stock_api.security.jwt.JwtAuthenticationFilter;
import fr.stockshop.stock_api.security.oauth2.CookieOAuth2AuthorizationRequestRepository;
import fr.stockshop.stock_api.security.oauth2.OAuth2AuthenticationFailureHandler;
import fr.stockshop.stock_api.security.oauth2.OAuth2AuthenticationSuccessHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuration Spring Security : API stateless authentifiée par JWT. Les endpoints publics (auth,
 * documentation, santé) sont explicitement listés, tout le reste nécessite un token valide.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private static final String[] PUBLIC_ENDPOINTS = {
    "/api/auth/register",
    "/api/auth/login",
    "/api/auth/refresh",
    "/api/auth/confirm-email",
    "/api/auth/resend-confirmation",
    "/api/auth/forgot-password",
    "/api/auth/reset-password",
    "/api/auth/oauth2/**",
    "/api/health",
    "/actuator/health",
    "/actuator/health/**",
    "/actuator/info",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html"
  };

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RequestLocaleFilter requestLocaleFilter;
  private final JwtAuthenticationEntryPoint authenticationEntryPoint;
  private final JwtAccessDeniedHandler accessDeniedHandler;
  private final AuthenticationProvider authenticationProvider;
  private final CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;
  private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
  private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(PUBLIC_ENDPOINTS).permitAll().anyRequest().authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authenticationProvider(authenticationProvider)
        // Flux OAuth2 Google (authorization code flow) : la requête d'autorisation (state/nonce)
        // est stockée dans un cookie plutôt qu'en session, l'API restant stateless.
        .oauth2Login(
            oauth2 ->
                oauth2
                    .authorizationEndpoint(
                        endpoint ->
                            endpoint
                                .baseUri("/api/auth/oauth2")
                                .authorizationRequestRepository(
                                    cookieAuthorizationRequestRepository))
                    .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/auth/oauth2/callback"))
                    .successHandler(oAuth2AuthenticationSuccessHandler)
                    .failureHandler(oAuth2AuthenticationFailureHandler))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(requestLocaleFilter, JwtAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of("*"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
