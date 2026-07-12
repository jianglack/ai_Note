package com.ainote.app.config;

import com.ainote.app.security.CustomUserDetailsService;
import com.ainote.app.security.JwtAuthenticationFilter;
import com.ainote.app.security.JwtTokenProvider;
import com.ainote.app.security.PrometheusScrapeTokenFilter;
import com.ainote.app.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtTokenProvider tokenProvider;
    private final TokenService tokenService;
    private final String corsAllowedOrigins;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          JwtTokenProvider tokenProvider,
                          TokenService tokenService,
                          @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String corsAllowedOrigins) {
        this.userDetailsService = userDetailsService;
        this.tokenProvider = tokenProvider;
        this.tokenService = tokenService;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(tokenProvider, userDetailsService, tokenService);
    }

    @Bean
    public PrometheusScrapeTokenFilter prometheusScrapeTokenFilter(
            @Value("${app.observability.prometheus-scrape-token:}") String token) {
        return new PrometheusScrapeTokenFilter(token);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(parseCorsAllowedOrigins(corsAllowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "X-Trace-Id", "Last-Event-ID", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private List<String> parseCorsAllowedOrigins(String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .map(this::requireExactHttpOrigin)
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException("app.cors.allowed-origins must contain at least one origin");
        }
        return origins;
    }

    private String requireExactHttpOrigin(String origin) {
        if (origin.contains("*")) {
            throw new IllegalStateException("CORS origins must not contain wildcards");
        }
        try {
            URI uri = new URI(origin);
            boolean validScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            boolean validPath = uri.getPath() == null || uri.getPath().isEmpty();
            if (!validScheme || uri.getHost() == null || uri.getUserInfo() != null
                    || !validPath || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException("CORS origins must be exact HTTP(S) origins");
            }
            return uri.getScheme().toLowerCase() + "://" + uri.getRawAuthority();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("CORS origin is invalid", e);
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           PrometheusScrapeTokenFilter prometheusScrapeTokenFilter) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(csrfRequestHandler)
                .ignoringRequestMatchers(request -> {
                    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
                    return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
                }))
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data:; " +
                    "connect-src 'self'; " +
                    "object-src 'none'; " +
                    "base-uri 'self'; " +
                    "frame-ancestors 'none'"
                ))
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 允许 ASYNC 分派通过（SSE 异步完成时触发）
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/auth/register",
                    "/api/auth/login",
                    "/api/auth/reset-password",
                    "/api/auth/password-reset/request",
                    "/api/auth/password-reset/confirm").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            // Cookie authentication must exist before CSRF decides whether a denial is 401 or 403.
            .addFilterBefore(jwtAuthenticationFilter(), CsrfFilter.class)
            .addFilterBefore(prometheusScrapeTokenFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
