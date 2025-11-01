package com.dbadmin.controller;

import com.dbadmin.service.ConnectionManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.dbadmin.model.QueryResult;

import java.util.*;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/sql")
@CrossOrigin(origins = "http://localhost:3000")
public class SqlController {

    @Autowired
    private ConnectionManagerService connectionManager;

    @PostMapping("/query")
    public ResponseEntity<?> executeQuery(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String sql = request.get("sql");

        if (sessionId == null || sql == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId and sql are required"));
        }

        try {
            List<Map<String, Object>> result = connectionManager.executeQuery(sessionId, sql);
            return ResponseEntity.ok(Map.of("data", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/execute")
    public ResponseEntity<?> executeSql(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        String sql = (String) request.get("sql");
        Integer page = (Integer) request.get("page");
        Integer pageSize = (Integer) request.get("pageSize");

        if (sessionId == null || sql == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId and sql are required"));
        }

        try {
            // 清理SQL字符串
            String cleanSql = sql.trim();
            String lowerSql = cleanSql.toLowerCase();

            // 检查事务命令
            if (lowerSql.startsWith("begin") || lowerSql.startsWith("start transaction")) {
                connectionManager.beginTransaction(sessionId);
                return ResponseEntity.ok(Map.of("message", "Transaction started"));
            } else if (lowerSql.startsWith("commit")) {
                connectionManager.commit(sessionId);
                return ResponseEntity.ok(Map.of("message", "Transaction committed"));
            } else if (lowerSql.startsWith("rollback")) {
                connectionManager.rollback(sessionId);
                return ResponseEntity.ok(Map.of("message", "Transaction rolled back"));
            }

            // 判断SQL类型
            if (isSelectQuery(cleanSql)) {
                // 执行查询语句（返回结果集）
                QueryResult result = connectionManager.executeQuery(sessionId, cleanSql, page, pageSize);

                // 构建响应
                Map<String, Object> response = new HashMap<>();
                response.put("data", result.getData());
                response.put("columns", result.getColumns());
                response.put("totalCount", result.getTotalCount());

                return ResponseEntity.ok(response);
            } else {
                // 执行更新语句（不返回结果集）
                int affectedRows = connectionManager.executeUpdate(sessionId, cleanSql);
                return ResponseEntity.ok(Map.of("affectedRows", affectedRows));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 判断是否为SELECT查询
     */
    private boolean isSelectQuery(String sql) {
        // 使用正则表达式匹配以SELECT开头的语句（排除WITH子句）
        String normalized = sql.toUpperCase().replaceAll("\\s+", " ").trim();

        // 检查是否是返回结果集的语句
        Pattern resultPattern = Pattern.compile("^(SELECT|SHOW|DESCRIBE|DESC|EXPLAIN|HELP|WITH)\\b");
        if (resultPattern.matcher(normalized).find()) {
            return true;
        }

        // 检查存储过程调用（可能返回结果集）
        if (normalized.startsWith("CALL ") || normalized.startsWith("EXEC ")) {
            return true;
        }

        // 检查表信息语句
        if (normalized.startsWith("TABLE ") || normalized.startsWith("COLUMNS ")) {
            return true;
        }

        return false;
    }

    @GetMapping("/tables/{sessionId}")
    public ResponseEntity<?> getTables(@PathVariable String sessionId, @RequestParam(required = false) String database) {
        try {
            List<String> tables = connectionManager.getTables(sessionId, database);
            return ResponseEntity.ok(Map.of("tables", tables));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/databases/{sessionId}")
    public ResponseEntity<?> getDatabases(@PathVariable String sessionId) {
        try {
            List<String> databases = connectionManager.getDatabases(sessionId);
            return ResponseEntity.ok(Map.of("databases", databases));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/switch-database/{sessionId}")
    public ResponseEntity<?> switchDatabase(@PathVariable String sessionId, @RequestBody Map<String, String> request) {
        String databaseName = request.get("database");
        if (databaseName == null || databaseName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Database name is required"));
        }

        try {
            connectionManager.switchDatabase(sessionId, databaseName);
            return ResponseEntity.ok(Map.of("message", "Switched to database: " + databaseName));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/current-database/{sessionId}")
    public ResponseEntity<?> getCurrentDatabase(@PathVariable String sessionId) {
        try {
            String database = connectionManager.getCurrentDatabase(sessionId);
            return ResponseEntity.ok(Map.of("database", database));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/views/{sessionId}")
    public ResponseEntity<?> getViews(@PathVariable String sessionId, @RequestParam(required = false) String database) {
        try {
            List<String> views = connectionManager.getViews(sessionId, database);
            return ResponseEntity.ok(Map.of("views", views));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/procedures/{sessionId}")
    public ResponseEntity<?> getProcedures(@PathVariable String sessionId, @RequestParam(required = false) String database) {
        try {
            List<String> procedures = connectionManager.getProcedures(sessionId, database);
            return ResponseEntity.ok(Map.of("procedures", procedures));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/functions/{sessionId}")
    public ResponseEntity<?> getFunctions(@PathVariable String sessionId, @RequestParam(required = false) String database) {
        try {
            List<String> functions = connectionManager.getFunctions(sessionId, database);
            return ResponseEntity.ok(Map.of("functions", functions));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/table-schema/{sessionId}")
    public ResponseEntity<?> getTableSchema(@PathVariable String sessionId, @RequestParam String tableName) {
        try {
            Map<String, Object> schema = connectionManager.getTableSchema(sessionId, tableName);
            return ResponseEntity.ok(schema);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}