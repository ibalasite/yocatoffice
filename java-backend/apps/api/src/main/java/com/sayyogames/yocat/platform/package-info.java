/**
 * Cross-cutting platform base (SecurityConfig / BearerTokenFilter support / rate-limit + cache infra /
 * observability / Outbox config — EDD §3.1b / §4.5.1). Declared an OPEN Spring Modulith module: it is shared
 * infrastructure that every bounded context may depend on (cache, web error types, CurrentUser), so all its
 * sub-packages are exposed. It holds no inbound dependency on any BC (the composition root and the global
 * exception advice live in the application root package), keeping the module graph acyclic (EDD §3.4 HC-5).
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.sayyogames.yocat.platform;
