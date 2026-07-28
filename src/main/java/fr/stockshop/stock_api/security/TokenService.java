package fr.stockshop.stock_api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Génère et hache les jetons à usage unique (confirmation de compte, réinitialisation de mot de
 * passe...).
 *
 * <p>Conformément aux notes techniques du ticket mail : le jeton en clair est transmis par email et
 * n'est jamais persisté. Seule son empreinte SHA-256 est stockée en base ; c'est cette empreinte
 * qui est comparée lors de la validation du lien reçu par l'utilisateur.
 */
@Component
public class TokenService {

  private static final int TOKEN_BYTE_LENGTH = 32;
  private static final String HASH_ALGORITHM = "SHA-256";

  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Génère un jeton aléatoire cryptographiquement sûr, encodé en Base64 URL-safe (sans padding),
   * destiné à être inclus tel quel dans le lien envoyé par email.
   */
  public String generateToken() {
    byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
    secureRandom.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  /**
   * Calcule l'empreinte SHA-256 (hexadécimale, minuscules) d'un jeton en clair, à des fins de
   * stockage/comparaison en base de données.
   */
  public String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 est garanti disponible dans toute JVM standard : ne devrait jamais se produire.
      throw new IllegalStateException("Algorithme de hachage indisponible : " + HASH_ALGORITHM, e);
    }
  }
}
