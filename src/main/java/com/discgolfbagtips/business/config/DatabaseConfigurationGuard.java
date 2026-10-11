package com.discgolfbagtips.business.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Refuses to start, naming the problem, when the database is not configured.
 *
 * <p>The production datasource settings deliberately have no defaults. Without this guard that
 * choice still fails, but badly: Spring Boot leaves an unresolvable {@code ${SUPABASE_DB_URL}} as
 * literal text, so the visible error becomes {@code 'url' must start with "jdbc"} — accurate, and
 * no help at all to someone looking at a deploy log at the other end of a missing environment
 * variable. This turns it into a message that says which variable and where to set it.
 *
 * <p>It runs as a {@link BeanFactoryPostProcessor} so that it fires before Flyway or the
 * connection pool are built, which is where the unhelpful errors would otherwise come from.
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseConfigurationGuard {

    private static final String[] REQUIRED = {
        "spring.datasource.url", "spring.datasource.username", "spring.datasource.password"
    };

    @Bean
    static BeanFactoryPostProcessor requireDatabaseConfiguration(Environment environment) {
        return beanFactory -> verify(environment);
    }

    static void verify(Environment environment) {
        for (String property : REQUIRED) {
            String value;
            try {
                value = environment.getProperty(property);
            } catch (IllegalArgumentException unresolved) {
                // e.g. Could not resolve placeholder 'SUPABASE_DB_URL' in value "${SUPABASE_DB_URL}"
                throw new IllegalStateException(
                        "The database is not configured: %s. Set SUPABASE_DB_URL, SUPABASE_DB_USER and "
                                .formatted(unresolved.getMessage())
                                + "SUPABASE_DB_PASSWORD in the environment (see docs/SUPABASE.md), or "
                                + "run with the 'local' profile for a local Postgres.",
                        unresolved);
            }
            if (value == null) {
                throw new IllegalStateException(
                        "The database is not configured: %s has no value. Set SUPABASE_DB_URL, "
                                .formatted(property)
                                + "SUPABASE_DB_USER and SUPABASE_DB_PASSWORD in the environment "
                                + "(see docs/SUPABASE.md), or run with the 'local' profile.");
            }
        }
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:")) {
            throw new IllegalStateException(
                    "SUPABASE_DB_URL must be a JDBC URL starting with \"jdbc:postgresql://\", but is \"%s\". "
                            .formatted(url)
                            + "Supabase shows a postgresql:// connection string in its dashboard; "
                            + "put jdbc: in front and move the credentials into the user and password variables.");
        }
    }
}
