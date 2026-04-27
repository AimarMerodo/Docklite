package es.docklite.docklitebackend.auth.jwt;

import es.docklite.docklitebackend.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static io.jsonwebtoken.security.Keys.hmacShaKeyFor;

@Component
public class JwtProvider {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey(){
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return hmacShaKeyFor(keyBytes);
    }

    // Crea el token JWT del usuario
    public String generateToken(User user){
        return Jwts.builder()
        .subject(user.getId().toString())
        .claim("username", user.getUsername())
        .claim("role", user.getRole().name())
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(getSigningKey())
        .compact();
    }

    private Claims getClaims(String token){
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token){
        return Long.parseLong(getClaims(token).getSubject());
    }

    public boolean validateToken(String token){
        try {
            getClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }


}
