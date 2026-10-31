// Source: templates/platform-server-bootstrap.md #6 (啟動 log: listening port + env + version) + EDD §3.6.2 / §13 A09.
package com.sayyogames.yocat.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Startup banner (bootstrap requirement #6). On {@link ApplicationReadyEvent} the process emits one
 * authoritative line identifying exactly which build is live: application name + version, the active Spring
 * profiles, the actually-bound HTTP port, and the OS PID, followed by the probe/metrics endpoints a K8s
 * operator needs (EDD §3.6.2 Health Check, §10.1 Observability). The line carries no secret, token, or PII
 * (EDD §13 OWASP A09) — only non-sensitive runtime coordinates.
 */
@Component
public class StartupInfoLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupInfoLogger.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ApplicationContext ctx = event.getApplicationContext();
        Environment env = ctx.getEnvironment();

        String name = env.getProperty("spring.application.name", "yocat-office-backend");
        String version = resolveVersion();
        String[] active = env.getActiveProfiles();
        String profiles = active.length == 0 ? "default" : String.join(",", active);
        String port = boundPort(ctx, env);

        log.info(
                "[startup] {} v{} ready — profiles=[{}] httpPort={} pid={} — "
                        + "liveness=/health/live readiness=/health/ready metrics=/actuator/prometheus",
                name, version, profiles, port, ProcessHandle.current().pid());
    }

    /** Jar manifest Implementation-Version (set by the Spring Boot build); "dev" when run from exploded classes. */
    private String resolveVersion() {
        String v = getClass().getPackage().getImplementationVersion();
        return (v == null || v.isBlank()) ? "dev" : v;
    }

    /** The port the servlet container actually bound (resolves RANDOM_PORT / env overrides), else configured value. */
    private String boundPort(ApplicationContext ctx, Environment env) {
        if (ctx instanceof WebServerApplicationContext web && web.getWebServer() != null) {
            return String.valueOf(web.getWebServer().getPort());
        }
        return env.getProperty("server.port", "8080");
    }
}
