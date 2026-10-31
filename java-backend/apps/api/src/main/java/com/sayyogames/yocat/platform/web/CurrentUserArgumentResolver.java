package com.sayyogames.yocat.platform.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves a {@link CurrentUser} controller-method parameter from the request attribute that the
 * {@code BearerTokenFilter} populates after it authenticates the opaque Bearer token.
 *
 * <p>A handler that declares a {@code CurrentUser} parameter is, by that declaration, an authenticated
 * endpoint: if the filter resolved no principal (missing / unknown / expired token), this resolver fails
 * with {@link UnauthorizedException} {@code "請先登入"} → HTTP 401 (API §5.2 self-scoped trust model).
 * Public endpoints simply omit the parameter and are never challenged here.</p>
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object principal = webRequest.getAttribute(CurrentUser.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (principal instanceof CurrentUser user) {
            return user;
        }
        throw new UnauthorizedException("請先登入");
    }
}
