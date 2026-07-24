package fr.stockshop.stock_api.security.jwt;

import fr.stockshop.stock_api.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Génération et validation des jetons JWT (access + refresh). Chaque token porte au minimum :
 * {@code sub} (email), {@code userId}, {@code email}, {@code iat} et {@code exp} (AC-4 du ticket
 * INF-003).
 */
@Component
public class JwtService {

  @Value("${security.jwt.secret}")
  private String secretKey;

  @Value("${security.jwt.access-token-expiration}")
  private long accessTokenExpiration;

  @Value("${security.jwt.refresh-token-expiration}")
  private long refreshTokenExpiration;

  public String generateAccessToken(User user) {
    return buildToken(user, accessTokenExpiration, "access");
  }

  public String generateRefreshToken(User user) {
    return buildToken(user, refreshTokenExpiration, "refresh");
  }

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public <T> T extractClaim(String token, Function<Claims, T> resolver) {
    Claims claims = extractAllClaims(token);
    return resolver.apply(claims);
  }

  public boolean isTokenValid(String token, UserDetails userDetails) {
    String username = extractUsername(token);
    return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
  }

  public long getAccessTokenExpiration() {
    return accessTokenExpiration;
  }

  private boolean isTokenExpired(String token) {
    return extractClaim(token, Claims::getExpiration).before(new Date());
  }

  private String buildToken(User user, long expirationMillis, String type) {
    Instant now = Instant.now();

    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", user.getId().toString());
    claims.put("email", user.getEmail());
    claims.put("type", type);

    return Jwts.builder()
        .claims(claims)
        .id(UUID.randomUUID().toString())
        .subject(user.getUsername())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(expirationMillis)))
        .signWith(getSigningKey())
        .compact();
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
  }

  private SecretKey getSigningKey() {
    byte[] keyBytes = Decoders.BASE64.decode(secretKey);
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
