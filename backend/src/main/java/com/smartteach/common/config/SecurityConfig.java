package com.smartteach.common.config;

import com.smartteach.common.security.SecurityProblemHandler;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/**",
            "/api/public/**",
            // STOMP handshake; the socket only pushes public room states (see WebSocketConfig).
            "/ws", "/ws/**",
            "/actuator/health", "/actuator/health/**",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            "/error"
    };

    /**
     * Metrics are for the Prometheus scraper only: HTTP Basic, user {@code prometheus}, password
     * from {@code METRICS_PASSWORD}. Without a password nobody can read them. Runs before the main
     * chain, which only knows JWT.
     */
    @Bean
    @Order(1)
    SecurityFilterChain metricsFilterChain(HttpSecurity http, PasswordEncoder encoder,
                                           @Value("${app.metrics.password:}") String password) throws Exception {
        http.securityMatcher("/actuator/prometheus")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults());
        if (password.isBlank()) {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll()).build();
        }
        var scraper = User.withUsername("prometheus").password(encoder.encode(password)).roles("METRICS").build();
        var provider = new DaoAuthenticationProvider(new InMemoryUserDetailsManager(scraper));
        provider.setPasswordEncoder(encoder);
        return http
                .authenticationManager(new ProviderManager(provider))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("METRICS"))
                .build();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problemHandler) throws Exception {
        return http
                // Stateless JWT in the Authorization header; the refresh cookie is only sent to
                // /api/auth with SameSite, so CSRF tokens are not needed.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .build();
    }

    /**
     * Ignore the Authorization header on auth endpoints, so an expired access token sent by the
     * browser does not block the refresh or login call.
     */
    private BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> request.getRequestURI().startsWith(request.getContextPath() + "/api/auth/")
                ? null
                : delegate.resolve(request);
    }

    /** Maps the {@code role} claim (TEACHER / ADMIN) to ROLE_TEACHER / ROLE_ADMIN. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // X-Attempt-Token / X-Player-Token: students have no account, see AttemptController.
        config.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-Attempt-Token", "X-Player-Token", "X-Request-Id"));
        // Readable by the frontend: the request id for error reports, Retry-After for 429.
        config.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
