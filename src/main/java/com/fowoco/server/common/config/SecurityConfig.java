package com.fowoco.server.common.config;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.security.core.userdetails.UserDetailsService;
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
    @Order(2)
    public SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            exceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    new ApiException(ErrorCode.AUTHENTICATION_REQUIRED)
            );
        };
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) ->
                exceptionResolver.resolveException(
                        request,
                        response,
                        null,
                        new ApiException(ErrorCode.ACCESS_DENIED)
                );

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
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout"
                        ).permitAll()
                        .requestMatchers("/error").permitAll()
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
}
