package dev.mvlcak.james.ai.tool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaSearchToolsTest {

    private EmbeddedDatabase db;
    private SchemaSearchTools tools;

    @BeforeEach
    void setup() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("""
                CREATE TABLE app_users (
                    id BIGINT PRIMARY KEY,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    tenant_id BIGINT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
        jdbc.execute("""
                CREATE TABLE app_orders (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    amount NUMERIC(10,2),
                    FOREIGN KEY (user_id) REFERENCES app_users(id)
                )""");
        jdbc.execute("""
                CREATE TABLE audit_log (
                    id BIGINT PRIMARY KEY,
                    message VARCHAR(500)
                )""");
        tools = new SchemaSearchTools(jdbc);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    // ---------- findTables ----------

    @Test
    void findTables_matchesWildcardPrefix() {
        List<Map<String, String>> matches = tools.findTables("app_us%");
        assertThat(matches).extracting("name").contains("APP_USERS");
    }

    @Test
    void findTables_matchesWildcardInfix() {
        List<Map<String, String>> matches = tools.findTables("%order%");
        assertThat(matches).extracting("name").contains("APP_ORDERS");
    }

    @Test
    void findTables_noMatchReturnsEmpty() {
        assertThat(tools.findTables("nothing_like_this%")).isEmpty();
    }

    @Test
    void findTables_returnsNameSchemaTypeCommentKeys() {
        List<Map<String, String>> matches = tools.findTables("app_users");
        assertThat(matches).hasSize(1);
        Map<String, String> row = matches.get(0);
        assertThat(row).containsKeys("name", "schema", "type", "comment")
                .containsEntry("name", "APP_USERS")
                .containsEntry("schema", "PUBLIC")
                .containsEntry("type", "BASE TABLE");
    }

    @Test
    void findTables_normalizesLowerCaseInput() {
        // Verifies storesUpperCaseIdentifiers() path in normalizeIdentifier
        assertThat(tools.findTables("app_users"))
                .extracting("name")
                .contains("APP_USERS");
    }

    // ---------- getTableScript ----------

    @Test
    void getTableScript_missingTable_returnsHelpfulMessage() {
        assertThat(tools.getTableScript("does_not_exist", null))
                .startsWith("-- Table not found");
    }

    @Test
    void getTableScript_startsWithCreateTable() {
        String ddl = tools.getTableScript("app_users", null);
        assertThat(ddl).startsWith("CREATE TABLE ")
                .contains("APP_USERS")
                .endsWith(");");
    }

    @Test
    void getTableScript_includesAllColumns() {
        String ddl = tools.getTableScript("app_users", null);
        assertThat(ddl)
                .contains("ID")
                .contains("EMAIL")
                .contains("TENANT_ID")
                .contains("CREATED_AT");
    }

    @Test
    void getTableScript_marksNotNullColumns() {
        String ddl = tools.getTableScript("app_users", null);
        assertThat(ddl).containsPattern("EMAIL[^,\\n]*NOT NULL")
                .doesNotContainPattern("TENANT_ID[^,\\n]*NOT NULL");
    }

    @Test
    void getTableScript_includesPrimaryKey() {
        String ddl = tools.getTableScript("app_users", null);
        assertThat(ddl).contains("PRIMARY KEY (ID)");
    }

    @Test
    void getTableScript_includesUniqueConstraint() {
        String ddl = tools.getTableScript("app_users", null);
        assertThat(ddl).contains("UNIQUE (EMAIL)");
    }

    @Test
    void getTableScript_includesForeignKey() {
        String ddl = tools.getTableScript("app_orders", null);
        assertThat(ddl)
                .contains("FOREIGN KEY (USER_ID) REFERENCES")
                .contains("APP_USERS")
                .contains("(ID)");
    }

    @Test
    void getTableScript_resolvesCaseInsensitiveFallback() {
        String ddl = tools.getTableScript("App_Users", null);
        assertThat(ddl).startsWith("CREATE TABLE ")
                .contains("APP_USERS");
    }

    @Test
    void getTableScript_formatsVarcharWithSize() {
        String ddl = tools.getTableScript("audit_log", null);
        assertThat(ddl).contains("MESSAGE")
                .contains("(500)");
    }

    @Test
    void getTableScript_formatsNumericWithPrecisionAndScale() {
        String ddl = tools.getTableScript("app_orders", null);
        assertThat(ddl).contains("AMOUNT")
                .containsPattern("\\(10,\\s*2\\)");
    }
}

