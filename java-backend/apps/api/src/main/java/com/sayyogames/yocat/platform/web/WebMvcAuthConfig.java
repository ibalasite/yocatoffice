package com.sayyogames.yocat.platform.web;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link CurrentUserArgumentResolver} so every bounded context's controller can receive the
 * authenticated {@link CurrentUser} (populated by the {@code BearerTokenFilter}) as a method parameter
 * instead of reaching into the member BC for token resolution (EDD §3.4 HC-5 DAG / §4.5.2).
 */
@Configuration
public class WebMvcAuthConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
