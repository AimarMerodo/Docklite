package es.docklite.docklitebackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * CORS applies in development (Angular dev server at :4200). In production,
 * the frontend (nginx) acts as a reverse proxy so everything is same-origin
 * and this filter normally never fires. APP_PUBLIC_URL is allowed as a safety
 * net for setups where an upstream proxy doesn't forward X-Forwarded-Proto
 * and the request would otherwise be misread as cross-origin.
 */
@Configuration
public class CorsConfig {

    private final String publicUrl;

    public CorsConfig(@Value("${app.public-url}") String publicUrl) {
        this.publicUrl = publicUrl;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        Set<String> origins = new LinkedHashSet<>();
        origins.add("http://localhost:4200");
        if (publicUrl != null && !publicUrl.isBlank()) {
            origins.add(publicUrl.replaceAll("/+$", ""));
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.copyOf(origins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
