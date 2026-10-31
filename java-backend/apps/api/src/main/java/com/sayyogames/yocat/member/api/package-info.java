/**
 * member BC Public NamedInterface — opaque Bearer session token → account resolution
 * ({@code SessionAuthenticator}, {@code AuthPrincipal}) consumed by the application-root
 * {@code BearerTokenFilter} (the single cross-cutting authentication seam, EDD §4.2 / §5.1). Exposed as the
 * member BC's public API; no other bounded context depends on it (the filter removed the per-controller member
 * coupling), so the module graph stays acyclic (EDD §3.4 HC-5).
 */
@org.springframework.modulith.NamedInterface("api")
package com.sayyogames.yocat.member.api;
