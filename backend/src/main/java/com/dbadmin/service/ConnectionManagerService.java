package com.dbadmin.service;

import com.dbadmin.model.ConnectionInfo;
import com.dbadmin.model.QueryResult;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConnectionManagerService {
    private final Map<String, Connection> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, ConnectionInfo> connectionInfoMap = new ConcurrentHashMap<>();

    public String createConnection(ConnectionInfo info) throws SQLException {
        String sessionId = UUID.randomUUID().toString();

        Connection conn = createPhysicalConnection(info);
        activeConnections.put(sessionId, conn);
        connectionInfoMap.put(sessionId, info);

        return sessionId;
    }

    public boolean testConnection(ConnectionInfo info) throws SQLException {
        try (Connection conn = createPhysicalConnection(info)) {
            return conn.isValid(5);
        } catch (SQLException e) {
            throw new SQLException("Connection test failed: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> executeQuery(String sessionId, String sql) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            return resultSetToList(rs);
        }
    }

    public QueryResult executeQuery(String sessionId, String sql, Integer page, Integer pageSize) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        // 添加分页逻辑
        String paginatedSql = sql;
        boolean needsCount = true;
        Long totalCount = 0L;

        // 检查是否已有LIMIT子句
        String upperSql = sql.toUpperCase().trim();
        if (!upperSql.contains(" LIMIT ")) {
            // 先查询总数
            String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS total_count";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countSql)) {
                if (rs.next()) {
                    totalCount = rs.getLong(1);
                }
            }

            // 添加LIMIT子句
            if (page != null && pageSize != null && pageSize > 0) {
                int offset = (page - 1) * pageSize;
                paginatedSql = sql + " LIMIT " + pageSize + " OFFSET " + offset;
            }
        } else {
            // 已有LIMIT，查询实际数据量作为总数
            needsCount = false;
        }

        // 执行分页查询
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(paginatedSql)) {

            List<Map<String, Object>> data = resultSetToList(rs);
            List<String> columns = new ArrayList<>();

            if (!data.isEmpty()) {
                columns.addAll(data.get(0).keySet());
            }

            // 如果没有LIMIT子句，使用实际返回的数据量作为总数
            if (needsCount == false) {
                totalCount = (long) data.size();
            }

            QueryResult result = new QueryResult(data, columns, totalCount);
            if (page != null) result.setCurrentPage(page);
            if (pageSize != null) result.setPageSize(pageSize);

            return result;
        }
    }

    public int executeUpdate(String sessionId, String sql) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        try (Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    // 事务相关方法
    public void beginTransaction(String sessionId) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        conn.setAutoCommit(false);
    }

    public void commit(String sessionId) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        conn.commit();
        conn.setAutoCommit(true);
    }

    public void rollback(String sessionId) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        conn.rollback();
        conn.setAutoCommit(true);
    }

    public boolean isInTransaction(String sessionId) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        return !conn.getAutoCommit();
    }

    public List<String> getTables(String sessionId, String database) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        // 如果没有指定数据库，使用当前连接的数据库
        if (database == null || database.trim().isEmpty()) {
            ConnectionInfo info = connectionInfoMap.get(sessionId);
            database = info != null ? info.getDatabase() : null;
        }

        try {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(database, null, "%", new String[]{"TABLE"})) {
                List<String> tables = new ArrayList<>();
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
                return tables;
            }
        } catch (SQLException e) {
            throw new SQLException("Failed to get tables: " + e.getMessage());
        }
    }

    public List<String> getViews(String sessionId, String database) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        if (database == null || database.trim().isEmpty()) {
            ConnectionInfo info = connectionInfoMap.get(sessionId);
            database = info != null ? info.getDatabase() : null;
        }

        try {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(database, null, "%", new String[]{"VIEW"})) {
                List<String> views = new ArrayList<>();
                while (rs.next()) {
                    views.add(rs.getString("TABLE_NAME"));
                }
                return views;
            }
        } catch (SQLException e) {
            throw new SQLException("Failed to get views: " + e.getMessage());
        }
    }

    public List<String> getProcedures(String sessionId, String database) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        if (database == null || database.trim().isEmpty()) {
            ConnectionInfo info = connectionInfoMap.get(sessionId);
            database = info != null ? info.getDatabase() : null;
        }

        try {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getProcedures(database, null, "%")) {
                List<String> procedures = new ArrayList<>();
                while (rs.next()) {
                    procedures.add(rs.getString("PROCEDURE_NAME"));
                }
                return procedures;
            }
        } catch (SQLException e) {
            throw new SQLException("Failed to get procedures: " + e.getMessage());
        }
    }

    public List<String> getFunctions(String sessionId, String database) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        if (database == null || database.trim().isEmpty()) {
            ConnectionInfo info = connectionInfoMap.get(sessionId);
            database = info != null ? info.getDatabase() : null;
        }

        List<String> functions = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW FUNCTION STATUS WHERE Db = '" + database + "'")) {
            while (rs.next()) {
                functions.add(rs.getString("Name"));
            }
        } catch (SQLException e) {
            throw new SQLException("Failed to get functions: " + e.getMessage());
        }
        return functions;
    }

    public Map<String, Object> getTableSchema(String sessionId, String tableName) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        Map<String, Object> schema = new HashMap<>();
        List<Map<String, Object>> columns = new ArrayList<>();
        List<Map<String, Object>> indexes = new ArrayList<>();

        // 获取表结构信息
        try (Statement stmt = conn.createStatement()) {
            // 获取列信息
            String columnsSql = String.format(
                "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT, " +
                "COLUMN_KEY, EXTRA, COLUMN_COMMENT, CHARACTER_MAXIMUM_LENGTH " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '%s' " +
                "ORDER BY ORDINAL_POSITION",
                tableName
            );

            try (ResultSet rs = stmt.executeQuery(columnsSql)) {
                while (rs.next()) {
                    Map<String, Object> column = new HashMap<>();
                    column.put("name", rs.getString("COLUMN_NAME"));
                    column.put("type", rs.getString("DATA_TYPE"));
                    column.put("nullable", "YES".equals(rs.getString("IS_NULLABLE")));
                    column.put("default", rs.getString("COLUMN_DEFAULT"));
                    column.put("key", rs.getString("COLUMN_KEY"));
                    column.put("extra", rs.getString("EXTRA"));
                    column.put("comment", rs.getString("COLUMN_COMMENT"));
                    column.put("maxLength", rs.getObject("CHARACTER_MAXIMUM_LENGTH"));
                    columns.add(column);
                }
            }

            // 获取索引信息
            String indexesSql = String.format(
                "SHOW INDEX FROM `%s`",
                tableName
            );

            Map<String, List<String>> indexMap = new HashMap<>();
            try (ResultSet rs = stmt.executeQuery(indexesSql)) {
                while (rs.next()) {
                    String indexName = rs.getString("Key_name");
                    String columnName = rs.getString("Column_name");

                    indexMap.computeIfAbsent(indexName, k -> new ArrayList<>()).add(columnName);
                }
            }

            for (Map.Entry<String, List<String>> entry : indexMap.entrySet()) {
                Map<String, Object> index = new HashMap<>();
                index.put("name", entry.getKey());
                index.put("columns", entry.getValue());
                index.put("unique", !entry.getKey().equals("PRIMARY"));
                indexes.add(index);
            }

            // 获取表注释
            String tableCommentSql = String.format(
                "SELECT TABLE_COMMENT " +
                "FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '%s'",
                tableName
            );

            String tableComment = "";
            try (ResultSet rs = stmt.executeQuery(tableCommentSql)) {
                if (rs.next()) {
                    tableComment = rs.getString("TABLE_COMMENT");
                }
            }

            schema.put("tableName", tableName);
            schema.put("comment", tableComment);
            schema.put("columns", columns);
            schema.put("indexes", indexes);
            schema.put("columnCount", columns.size());

            return schema;
        } catch (SQLException e) {
            throw new SQLException("Failed to get table schema: " + e.getMessage());
        }
    }

    public List<String> getDatabases(String sessionId) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        List<String> databases = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                String dbName = rs.getString(1);
                // 过滤系统数据库
                if (!"information_schema".equals(dbName) &&
                    !"performance_schema".equals(dbName) &&
                    !"mysql".equals(dbName) &&
                    !"sys".equals(dbName)) {
                    databases.add(dbName);
                }
            }
        } catch (SQLException e) {
            throw new SQLException("Failed to get databases: " + e.getMessage());
        }
        return databases;
    }

    public void switchDatabase(String sessionId, String databaseName) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("USE `" + databaseName + "`");

            // 更新连接信息中的数据库名
            ConnectionInfo info = connectionInfoMap.get(sessionId);
            if (info != null) {
                info.setDatabase(databaseName);
            }
        } catch (SQLException e) {
            throw new SQLException("Failed to switch database: " + e.getMessage());
        }
    }

    public String getCurrentDatabase(String sessionId) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DATABASE()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        } catch (SQLException e) {
            throw new SQLException("Failed to get current database: " + e.getMessage());
        }
    }

    public void closeConnection(String sessionId) {
        Connection conn = activeConnections.remove(sessionId);
        connectionInfoMap.remove(sessionId);
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                // Ignore
            }
        }
    }

    public ConnectionInfo getConnectionInfo(String sessionId) {
        return connectionInfoMap.get(sessionId);
    }

    public boolean isValidConnection(String sessionId) {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null) return false;
        try {
            return !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private Connection createPhysicalConnection(ConnectionInfo info) throws SQLException {
        String database = info.getDatabase();
        if (database == null || database.trim().isEmpty()) {
            // 不指定数据库，连接到服务器
            database = "";
        }

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true",
                info.getHost(), info.getPort(), database,
                "REQUIRED".equalsIgnoreCase(info.getSslMode()) ? "true" : "false");

        Properties props = new Properties();
        props.setProperty("user", info.getUsername());
        props.setProperty("password", info.getPassword());
        props.setProperty("serverTimezone", "UTC");

        return DriverManager.getConnection(url, props);
    }

    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<Map<String, Object>> list = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnLabel(i);
                Object value = rs.getObject(i);
                row.put(columnName, value);
            }
            list.add(row);
        }

        return list;
    }
}