package fr.stockshop.stock_api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenServiceTest {

  private final TokenService tokenService = new TokenService();

  @Test
  void generateTokenShouldReturnNonEmptyUrlSafeValue() {
    String token = tokenService.generateToken();

    assertThat(token).isNotBlank();
    // Base64 URL-safe : ne doit jamais contenir '+' ou '/' (uniquement utilisés par le Base64
    // standard)
    assertThat(token).doesNotContain("+", "/", "=");
  }

  @Test
  void generateTokenShouldReturnDifferentValuesEachTime() {
    String first = tokenService.generateToken();
    String second = tokenService.generateToken();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void hashTokenShouldBeDeterministic() {
    String rawToken = "un-jeton-de-test";

    assertThat(tokenService.hashToken(rawToken)).isEqualTo(tokenService.hashToken(rawToken));
  }

  @Test
  void hashTokenShouldReturnLowercaseHexSha256() {
    String hash = tokenService.hashToken("un-jeton-de-test");

    // SHA-256 -> 32 octets -> 64 caractères hexadécimaux
    assertThat(hash).hasSize(64);
    assertThat(hash).matches("[0-9a-f]+");
  }

  @Test
  void hashTokenShouldDifferForDifferentInputs() {
    String hash1 = tokenService.hashToken("jeton-a");
    String hash2 = tokenService.hashToken("jeton-b");

    assertThat(hash1).isNotEqualTo(hash2);
  }

  @Test
  void hashOfGeneratedTokenShouldNeverEqualTheRawToken() {
    String rawToken = tokenService.generateToken();

    assertThat(tokenService.hashToken(rawToken)).isNotEqualTo(rawToken);
  }
}
