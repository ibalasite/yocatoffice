package com.sayyogames.yocat;

import com.sayyogames.yocat.member.api.SessionAuthenticator;
import com.sayyogames.yocat.platform.cache.RateLimiter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Application-root web infrastructure wiring. Registers the cross-cutting {@link BearerTokenFilter} (member
 * token resolution + platform rate limiting) at high precedence so it authenticates and rate-limits every
 * request before it reaches a controller (EDD §4.5.1 "RateLimitFilter 在 auth filter 之前" is satisfied by a
 * single ordered filter that rate-limits after resolving the principal, EDD §11.3C).
 *
 * <p>Lives in the application root package (outside the Spring Modulith bounded contexts) because it composes
 * the member authenticator with the platform rate limiter — a legitimate application-level seam.</p>
 */
@Configuration
public class WebInfrastructureConfig {

    @Bean
    public FilterRegistrationBean<BearerTokenFilter> bearerTokenFilter(
            SessionAuthenticator sessionAuthenticator, RateLimiter rateLimiter) {
        FilterRegistrationBean<BearerTokenFilter> registration =
                new FilterRegistrationBean<>(new BearerTokenFilter(sessionAuthenticator, rateLimiter));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        registration.setName("bearerTokenFilter");
        return registration;
    }
}
