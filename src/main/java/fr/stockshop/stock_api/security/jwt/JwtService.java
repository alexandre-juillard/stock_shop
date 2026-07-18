package fr.stockshop.stock_api.security.jwt;

import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.time.Instant;

/**
 * Génération et validation des jetons JWT (access + refresh).
 */
@Component
public class JwtService {

	@Value("${security.jwt.secret}")
	private String secretKey;

	@Value("${security.jwt.access-token-expiration}")
	private long accessTokenExpiration;

	@Value("${security.jwt.refresh-token-expiration}")
	private long refreshTokenExpiration;

	public String generateAccessToken(UserDetails userDetails) {
		return buildToken(userDetails, accessTokenExpiration, Map.of("type", "access"));
	}

	public String generateRefreshToken(UserDetails userDetails) {
		return buildToken(userDetails, refreshTokenExpiration, Map.of("type", "refresh"));
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

	private String buildToken(UserDetails userDetails, long expirationMillis, Map<String, Object> claims) {
		Instant now = Instant.now();
		return Jwts.builder()
				.claims(claims)
				.subject(userDetails.getUsername())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusMillis(expirationMillis)))
				.signWith(getSigningKey())
				.compact();
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parser()
				.verifyWith(getSigningKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	private SecretKey getSigningKey() {
		byte[] keyBytes = Decoders.BASE64.decode(secretKey);
		return Keys.hmacShaKeyFor(keyBytes);
	}
}

