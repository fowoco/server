package com.fowoco.server.common.config;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Prevents Boot from generating a temporary password because this service authenticates with JWT only.
     */
    @Bean
    public UserDetailsService emptyUserDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    @Order(1)
    @Profile("observability & !prod")
    public SecurityFilterChain prometheusSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/prometheus")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    /**
     * observability 프로필이 아닌 환경(특히 prod)에서 /actuator/prometheus에 붙는 체인.
     * prod에서 permitAll로 열면 /actuator/**가 이미 public ingress로 노출돼 있어 내부
     * 지표가 인증 없이 인터넷에 공개된다 — 그래서 이 체인은 스크레이핑 전용 Basic Auth
     * 계정 하나만 허용한다. app.observability.prometheus-scrape-password가 비어 있으면
     * (기본값) 그 계정 자체를 안 만들고 전부 거부한다 — "설정 안 하면 막힘"이 기본.
     */
    @Bean
    @Order(2)
    @Profile("!(observability & !prod)")
    public SecurityFilterChain prometheusScrapeAuthSecurityFilterChain(
            HttpSecurity http,
            PasswordEncoder passwordEncoder,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            @Value("${app.observability.prometheus-scrape-password:}") String scrapePassword
    ) throws Exception {
        http.securityMatcher("/actuator/prometheus");
        if (scrapePassword.isBlank()) {
            // 계정 자체가 없으니 인증을 시도해도 항상 거부 — applicationSecurityFilterChain과
            // 같은 401 응답 형태(AUTHENTICATION_REQUIRED)로 통일한다.
            http
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(authenticationEntryPoint(exceptionResolver))
                            .accessDeniedHandler(accessDeniedHandler(exceptionResolver))
                    );
        } else {
            UserDetailsService scrapeUserDetailsService = new InMemoryUserDetailsManager(
                    User.withUsername("prometheus")
                            .password(passwordEncoder.encode(scrapePassword))
                            .roles("PROMETHEUS_SCRAPE")
                            .build()
            );
            http
                    .userDetailsService(scrapeUserDetailsService)
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole("PROMETHEUS_SCRAPE"))
                    .httpBasic(Customizer.withDefaults());
        }
        http
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    @Bean
    @Order(3)
    @Profile("local")
    public SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/h2-console/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));
        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = authenticationEntryPoint(exceptionResolver);
        AccessDeniedHandler accessDeniedHandler = accessDeniedHandler(exceptionResolver);

        http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/health",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/signup-policy").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password-reset-requests",
                                "/api/v1/auth/password-resets"
                        ).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/worker-links/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/worker-links/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/**")
                        .hasAnyRole("ADMIN", "HR", "VIEWER")
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/**")
                        .hasAnyRole("ADMIN", "HR", "VIEWER")
                        .requestMatchers("/api/v1/**").hasAnyRole("ADMIN", "HR")
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    private static AuthenticationEntryPoint authenticationEntryPoint(HandlerExceptionResolver exceptionResolver) {
        return (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            exceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    new ApiException(ErrorCode.AUTHENTICATION_REQUIRED)
            );
        };
    }

    private static AccessDeniedHandler accessDeniedHandler(HandlerExceptionResolver exceptionResolver) {
        return (request, response, exception) ->
                exceptionResolver.resolveException(
                        request,
                        response,
                        null,
                        new ApiException(ErrorCode.ACCESS_DENIED)
                );
    }
}
