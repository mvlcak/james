package dev.mvlcak.james.ai.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class SchemaSearchTools {

    public static final String TABLE_SCHEM = "TABLE_SCHEM";
    public static final String TABLE_NAME = "TABLE_NAME";
    public static final String TABLE_TYPE = "TABLE_TYPE";
    public static final String REMARKS = "REMARKS";
    public static final String COLUMN_NAME = "COLUMN_NAME";
    private final JdbcTemplate jdbcTemplate;

    public SchemaSearchTools(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static SchemaSearchTools.Builder builder() {
        return new SchemaSearchTools.Builder();
    }

    public static class Builder {
        public SchemaSearchTools build(JdbcTemplate jdbcTemplate) {
            return new SchemaSearchTools(jdbcTemplate);
        }
    }

    @Tool(description = """
            Search for tables by name pattern. Use SQL LIKE wildcards:
            % matches any sequence, _ matches a single character.
            Examples: 'user%' finds users, user_roles; '%order%' finds orders, order_items, purchase_orders.
            Returns matching tables with their schema and comment. Call this FIRST to discover
            relevant tables before requesting their CREATE scripts.""")
    public List<Map<String, String>> findTables(
            @ToolParam(description = "Name pattern with SQL LIKE wildcards (% and _)") String namePattern) {

        return jdbcTemplate.execute((Connection conn) -> {
            List<Map<String, String>> matches = new ArrayList<>();
            DatabaseMetaData meta = conn.getMetaData();
            String normalized = normalizeIdentifier(meta, namePattern);

            try (ResultSet rs = meta.getTables(
                    conn.getCatalog(), null, normalized,
                    new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    Map<String, String> match = new LinkedHashMap<>();
                    match.put("name", rs.getString(TABLE_NAME));
                    match.put("schema", nullSafe(rs.getString(TABLE_SCHEM)));
                    match.put("type", rs.getString(TABLE_TYPE));
                    match.put("comment", nullSafe(rs.getString(REMARKS)));
                    matches.add(match);
                }
            }
            return matches;
        });
    }

    @Tool(description = """
            Get the CREATE TABLE script for a specific table, including all columns with
            their types, NOT NULL constraints, default values, PRIMARY KEY, FOREIGN KEYs,
            and UNIQUE constraints. Use this AFTER findTables to get detailed schema
            information needed to write correct SQL queries.""")
    public String getTableScript(
            @ToolParam(description = "Exact table name (case-sensitive on some databases)")
            String tableName,
            @ToolParam(description = "Schema name. Optional — omit to search all schemas.", required = false)
            String schema) {

        return jdbcTemplate.execute((Connection conn) -> {
            DatabaseMetaData meta = conn.getMetaData();
            String normalizedTable = normalizeIdentifier(meta, tableName);
            String normalizedSchema = schema == null ? null : normalizeIdentifier(meta, schema);

            // Resolve the table (handles case-sensitivity differences)
            ResolvedTable resolved = resolveTable(meta, conn.getCatalog(), normalizedSchema, normalizedTable);
            if (resolved == null) {
                return "-- Table not found: " + tableName
                        + ". Try findTables() to discover the correct name.";
            }

            return buildCreateScript(meta, resolved);
        });
    }

    // DDL generation

    private String buildCreateScript(DatabaseMetaData meta, ResolvedTable t) throws SQLException {
        StringBuilder ddl = new StringBuilder();

        ddl.append("CREATE TABLE ");
        if (t.schema != null && !t.schema.isBlank()) {
            ddl.append(t.schema).append(".");
        }
        ddl.append(t.name).append(" (\n");

        List<String> lines = new ArrayList<>();
        Set<String> pkColumns = getPrimaryKeyColumns(meta, t);

        // Columns
        try (ResultSet rs = meta.getColumns(t.catalog, t.schema, t.name, "%")) {
            while (rs.next()) {
                StringBuilder col = new StringBuilder("    ");
                String colName = rs.getString(COLUMN_NAME);
                col.append(colName).append(" ");
                col.append(formatType(rs));

                if (rs.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls) {
                    col.append(" NOT NULL");
                }

                String defaultVal = rs.getString("COLUMN_DEF");
                if (defaultVal != null && !defaultVal.isBlank()) {
                    col.append(" DEFAULT ").append(defaultVal.trim());
                }

                String comment = rs.getString(REMARKS);
                if (comment != null && !comment.isBlank()) {
                    col.append("  -- ").append(comment.replace("\n", " "));
                }
                lines.add(col.toString());
            }
        }

        // Primary key
        if (!pkColumns.isEmpty()) {
            lines.add("    PRIMARY KEY (" + String.join(", ", pkColumns) + ")");
        }

        // Unique constraints (from indexes marked unique, excluding the PK)
        List<String> uniques = getUniqueConstraints(meta, t, pkColumns);
        lines.addAll(uniques);

        // Foreign keys
        try (ResultSet rs = meta.getImportedKeys(t.catalog, t.schema, t.name)) {
            // FKs can span multiple rows when composite — group by FK name
            Map<String, FkInfo> fks = new LinkedHashMap<>();
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String key = fkName != null ? fkName : UUID.randomUUID().toString();
                String pkSchema = rs.getString("PKTABLE_SCHEM");
                String pkTable = rs.getString("PKTABLE_NAME");
                fks.computeIfAbsent(key, k -> new FkInfo(pkSchema, pkTable));
                fks.get(key).localCols.add(rs.getString("FKCOLUMN_NAME"));
                fks.get(key).refCols.add(rs.getString("PKCOLUMN_NAME"));
            }
            for (FkInfo fk : fks.values()) {
                String refTable = (fk.refSchema != null ? fk.refSchema + "." : "") + fk.refTable;
                lines.add(String.format("    FOREIGN KEY (%s) REFERENCES %s(%s)",
                        String.join(", ", fk.localCols),
                        refTable,
                        String.join(", ", fk.refCols)));
            }
        } catch (SQLException ex) {
            return ex.getMessage();
        }

        ddl.append(String.join(",\n", lines));
        ddl.append("\n);");

        // Table-level comment
        String tableComment = getTableComment(meta, t);
        if (tableComment != null && !tableComment.isBlank()) {
            ddl.append("\n-- ").append(tableComment.replace("\n", " "));
        }

        return ddl.toString();
    }

    private String formatType(ResultSet rs) throws SQLException {
        String typeName = rs.getString("TYPE_NAME");
        int size = rs.getInt("COLUMN_SIZE");
        int decimals = rs.getInt("DECIMAL_DIGITS");
        boolean hasDecimals = !rs.wasNull() && decimals > 0;

        // Types that take (size) in DDL
        String upper = typeName.toUpperCase();
        if (upper.contains("CHAR") || upper.equals("VARCHAR") || upper.contains("TEXT")) {
            return size > 0 && size < Integer.MAX_VALUE
                    ? typeName + "(" + size + ")"
                    : typeName;
        }
        if (upper.equals("NUMERIC") || upper.equals("DECIMAL")) {
            return hasDecimals
                    ? typeName + "(" + size + "," + decimals + ")"
                    : typeName + "(" + size + ")";
        }
        return typeName;
    }

    private Set<String> getPrimaryKeyColumns(DatabaseMetaData meta, ResolvedTable t) throws SQLException {
        // Use TreeMap to preserve key sequence order
        Map<Short, String> byPosition = new TreeMap<>();
        try (ResultSet rs = meta.getPrimaryKeys(t.catalog, t.schema, t.name)) {
            while (rs.next()) {
                byPosition.put(rs.getShort("KEY_SEQ"), rs.getString(COLUMN_NAME));
            }
        }
        return new LinkedHashSet<>(byPosition.values());
    }

    private List<String> getUniqueConstraints(DatabaseMetaData meta, ResolvedTable t, Set<String> pkColumns) throws SQLException {
        // Group unique indexes by index name, skip PK
        Map<String, List<String>> uniqueIndexes = new LinkedHashMap<>();
        try (ResultSet rs = meta.getIndexInfo(t.catalog, t.schema, t.name, true, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                String colName = rs.getString(COLUMN_NAME);
                if (indexName == null || colName == null) continue;
                uniqueIndexes.computeIfAbsent(indexName, k -> new ArrayList<>()).add(colName);
            }
        }

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : uniqueIndexes.entrySet()) {
            // Skip if this index matches the PK exactly
            if (new HashSet<>(e.getValue()).equals(pkColumns)) continue;
            result.add("    UNIQUE (" + String.join(", ", e.getValue()) + ")");
        }
        return result;
    }

    private String getTableComment(DatabaseMetaData meta, ResolvedTable t) throws SQLException {
        try (ResultSet rs = meta.getTables(t.catalog, t.schema, t.name, null)) {
            if (rs.next()) {
                return rs.getString(REMARKS);
            }
        }
        return null;
    }

    // Case-sensitivity handling

    private ResolvedTable resolveTable(DatabaseMetaData meta, String catalog, String schema, String tableName) throws SQLException {
        // First try exact match
        try (ResultSet rs = meta.getTables(catalog, schema, tableName, null)) {
            if (rs.next()) {
                return new ResolvedTable(rs.getString("TABLE_CAT"), rs.getString(TABLE_SCHEM), rs.getString(TABLE_NAME));
            }
        }
        // Fall back to case-insensitive search
        try (ResultSet rs = meta.getTables(catalog, schema, "%", null)) {
            while (rs.next()) {
                if (tableName.equalsIgnoreCase(rs.getString(TABLE_NAME))) {
                    return new ResolvedTable(rs.getString("TABLE_CAT"), rs.getString(TABLE_SCHEM), rs.getString(TABLE_NAME));
                }
            }
        }
        return null;
    }

    private String normalizeIdentifier(DatabaseMetaData meta, String identifier) throws SQLException {
        if (identifier == null) return null;
        if (meta.storesUpperCaseIdentifiers()) return identifier.toUpperCase();
        if (meta.storesLowerCaseIdentifiers()) return identifier.toLowerCase();
        return identifier;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private record ResolvedTable(String catalog, String schema, String name) {
    }

    private static class FkInfo {
        final String refSchema;
        final String refTable;
        final List<String> localCols = new ArrayList<>();
        final List<String> refCols = new ArrayList<>();

        FkInfo(String refSchema, String refTable) {
            this.refSchema = refSchema;
            this.refTable = refTable;
        }
    }

}
