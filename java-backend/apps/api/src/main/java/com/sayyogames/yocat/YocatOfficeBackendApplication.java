// Source: docs/EDD.md §3.1b/§3.3/§9 + docs/blueprint/scaffold
package com.sayyogames.yocat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * yocat-office-backend — Spring Modulith Modular Monolith.
 * 7 Bounded Contexts: member, wallet, deposit, entitlement, record, leaderboard, gamesave.
 * Each BC directory maps to one @ApplicationModule; cross-BC access only via the
 * BC's public api NamedInterface or a Domain Event (EDD section 2 / 4.3).
 */
@Modulithic(systemName = "yocat-office-backend")
@SpringBootApplication
public class YocatOfficeBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(YocatOfficeBackendApplication.class, args);
    }
}
