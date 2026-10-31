package com.sayyogames.yocat.platform.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Web security baseline for the Phase-A REST API (EDD §7 / API §5.2 self-scoped trust model).
 *
 * <p>Spring Security is on the classpath (opaque-session Bearer auth, EDD §3.3). The authoritative
 * Bearer-token authentication runs in the application {@code BearerTokenFilter} (O-09), which resolves the
 * opaque token to a {@code CurrentUser} request attribute and applies the EDD §11.3C rate limits. This chain
 * therefore disables Spring Security's default form-login / HTTP-Basic / CSRF gate and permits every request to
 * reach that filter + the controllers, where the real 401 (via {@code CurrentUserArgumentResolver}) and 429
 * checks happen. CSRF is disabled because the API is a stateless, token-authenticated JSON API (no
 * cookies/session), per API §5.2.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }
}
