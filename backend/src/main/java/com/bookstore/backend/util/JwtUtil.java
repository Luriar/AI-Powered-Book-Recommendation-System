package com.bookstore.backend.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private static final String SECRET = java.util.Objects.requireNonNull(System.getenv("JWT_SECRET"), "JWT_SECRET environment variable is required");
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final long EXPIRATION_TIME = 1000 * 60 * 60; // 1시간

    //  role을 포함하는 토큰 생성
    public String generateToken(String email, String role) {
        return Jwts.builder()
                .setSubject(email)
                .addClaims(Map.of("role", role))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SignatureException e) {
            log.error("X 서명 검증 실패: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("X JWT 형식 오류: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("X JWT 만료됨: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("X 지원하지 않는 JWT: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("X JWT 파싱 실패: {}", e.getMessage());
        } catch (Exception e) {
            log.error("X 알 수 없는 JWT 검증 오류: {}", e.getMessage());
        }
        return false;
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).getSubject();
    }

    //  role 가져오기 메서드 추가
    public String getUserRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
