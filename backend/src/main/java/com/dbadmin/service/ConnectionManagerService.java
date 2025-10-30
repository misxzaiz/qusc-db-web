package com.dbadmin.service;

import com.dbadmin.model.ConnectionInfo;
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

    public int executeUpdate(String sessionId, String sql) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        try (Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    public List<String> getTables(String sessionId) throws SQLException {
        Connection conn = activeConnections.get(sessionId);
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection not found or closed");
        }

        try {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
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
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true",
                info.getHost(), info.getPort(), info.getDatabase(),
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