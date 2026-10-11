package com.discgolfbagtips.business.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseConfigurationGuardTest {

    private MockEnvironment configured() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example:5432/postgres")
                .withProperty("spring.datasource.username", "postgres.abcd")
                .withProperty("spring.datasource.password", "secret");
    }

    @Test
    void acceptsACompleteConfiguration() {
        assertThatCode(() -> DatabaseConfigurationGuard.verify(configured())).doesNotThrowAnyException();
    }

    /** The deploy mistake: the variable never reached the container, so the placeholder is unresolved. */
    @Test
    void namesTheVariableWhenAPlaceholderCannotBeResolved() {
        MockEnvironment environment = configured()
                .withProperty("spring.datasource.url", "${SUPABASE_DB_URL}");

        assertThatThrownBy(() -> DatabaseConfigurationGuard.verify(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPABASE_DB_URL")
                .hasMessageContaining("docs/SUPABASE.md");
    }

    @Test
    void namesTheOtherTwoVariablesToo() {
        MockEnvironment noPassword = configured()
                .withProperty("spring.datasource.password", "${SUPABASE_DB_PASSWORD}");

        assertThatThrownBy(() -> DatabaseConfigurationGuard.verify(noPassword))
                .hasMessageContaining("SUPABASE_DB_PASSWORD");
    }

    @Test
    void rejectsTheConnectionStringSupabaseActuallyShowsInItsDashboard() {
        MockEnvironment environment = configured()
                .withProperty("spring.datasource.url", "postgresql://postgres.abcd:pw@host:5432/postgres");

        assertThatThrownBy(() -> DatabaseConfigurationGuard.verify(environment))
                .hasMessageContaining("must be a JDBC URL")
                .hasMessageContaining("jdbc:");
    }

    /** The local profile uses an empty password against a trust-auth Postgres, which is legitimate. */
    @Test
    void anEmptyPasswordIsAllowed() {
        MockEnvironment environment = configured().withProperty("spring.datasource.password", "");

        assertThat(environment.getProperty("spring.datasource.password")).isEmpty();
        assertThatCode(() -> DatabaseConfigurationGuard.verify(environment)).doesNotThrowAnyException();
    }
}
