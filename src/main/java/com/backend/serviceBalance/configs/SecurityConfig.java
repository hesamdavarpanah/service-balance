package com.backend.serviceBalance.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, @Value("${base-url}") String baseUrl) {
        String basePath = baseUrl.replaceAll("/+$", "");

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable).formLogin(ServerHttpSecurity.FormLoginSpec::disable).httpBasic(ServerHttpSecurity.HttpBasicSpec::disable).authorizeExchange(auth -> auth.pathMatchers(HttpMethod.POST, basePath + "/accounts/create-account/").permitAll().pathMatchers(HttpMethod.GET, basePath + "/accounts/retrieve-accounts/").permitAll().pathMatchers(HttpMethod.PUT, basePath + "/accounts/update-account/").permitAll().pathMatchers(HttpMethod.POST, basePath + "/transactions/create-transaction/").permitAll().pathMatchers(HttpMethod.GET, basePath + "/transactions/retrieve-transactions/").permitAll().pathMatchers(HttpMethod.POST, basePath + "/balances/credit/", basePath + "/balances/debit/", basePath + "/balances/transfer/", basePath + "/balances/get-balance/").permitAll().pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll().anyExchange().authenticated()).exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))).build();
    }
}