package com.sayyogames.yocat.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * JUnit Platform Suite entry point for the server-side BDD step definitions (O-25, TDD RED).
 *
 * <p>Runs every Gherkin scenario under {@code classpath:features} (the 9 feature files copied from
 * {@code features/**}, 65 scenarios) against the real Spring Boot application context booted by
 * {@link CucumberSpringConfiguration} (random-port web server + Testcontainers PostgreSQL 16).</p>
 *
 * <p><b>Naming:</b> the class ends in {@code IT} so it is bound to the Failsafe plugin and excluded
 * from the unit Surefire run ({@code **}{@code /*Test.java}); it never runs during {@code mvn test}.
 * The RED bar for this sub-step is {@code mvn -o test-compile} (exit 0): the member/wallet/… REST
 * controllers are implemented by the downstream {@code contract-controllers} GREEN sub-step, so until
 * then the {@code @api}-tagged scenarios fail at runtime (endpoints return 404 / no provider) — this
 * is the executable contract encoded ahead of the implementation.</p>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sayyogames.yocat.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, summary")
public class RunBddContractIT {
}
