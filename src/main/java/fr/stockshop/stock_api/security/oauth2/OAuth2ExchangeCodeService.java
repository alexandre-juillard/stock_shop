package fr.stockshop.stock_api.security.oauth2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.stockshop.stock_api.exception.InvalidExchangeCodeException;
import fr.stockshop.stock_api.security.TokenService;
import fr.stockshop.stock_api.user.dto.OAuth2LoginOutcome;
import fr.stockshop.stock_api.user.entity.OauthExchangeCode;
import fr.stockshop.stock_api.user.repository.OauthExchangeCodeRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Émet et consomme les codes d'échange à usage unique utilisés pour transmettre le résultat d'une
 * connexion OAuth2 (jetons, ou demande de liaison de compte) à l'app mobile via un deep link,
 * plutôt que d'inclure directement des jetons sensibles dans l'URL de redirection.
 */
@Service
@RequiredArgsConstructor
public class OAuth2ExchangeCodeService {

  private final OauthExchangeCodeRepository repository;
  private final TokenService tokenService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${app.oauth2.exchange-code-expiration:2m}")
  private Duration exchangeCodeExpiration;

  @Transactional
  public String create(OAuth2LoginOutcome outcome) {
    String rawCode = tokenService.generateToken();
    repository.save(
        OauthExchangeCode.builder()
            .codeHash(tokenService.hashToken(rawCode))
            .payload(serialize(outcome))
            .expiresAt(Instant.now().plus(exchangeCodeExpiration))
            .build());
    return rawCode;
  }

  /** Le code est à usage unique : il est supprimé dès sa première consommation, réussie ou non. */
  @Transactional
  public OAuth2LoginOutcome consume(String rawCode) {
    OauthExchangeCode entity =
        repository
            .findByCodeHash(tokenService.hashToken(rawCode))
            .orElseThrow(InvalidExchangeCodeException::new);
    repository.delete(entity);

    if (entity.getExpiresAt().isBefore(Instant.now())) {
      throw new InvalidExchangeCodeException();
    }
    return deserialize(entity.getPayload());
  }

  private String serialize(OAuth2LoginOutcome outcome) {
    try {
      return objectMapper.writeValueAsString(outcome);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Impossible de sérialiser le résultat OAuth2", e);
    }
  }

  private OAuth2LoginOutcome deserialize(String payload) {
    try {
      return objectMapper.readValue(payload, OAuth2LoginOutcome.class);
    } catch (JsonProcessingException e) {
      throw new InvalidExchangeCodeException();
    }
  }
}
