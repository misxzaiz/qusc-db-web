package com.dbadmin.controller;

import com.dbadmin.service.ConnectionManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<?> executeSql(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String sql = request.get("sql");

        if (sessionId == null || sql == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId and sql are required"));
        }

        try {
            // 清理SQL字符串
            String cleanSql = sql.trim();

            // 判断SQL类型
            if (isSelectQuery(cleanSql)) {
                // 执行查询语句（返回结果集）
                List<Map<String, Object>> result = connectionManager.executeQuery(sessionId, cleanSql);

                // 构建响应
                Map<String, Object> response = new HashMap<>();
                response.put("data", result);

                // 获取列名
                if (!result.isEmpty()) {
                    response.put("columns", new ArrayList<>(result.get(0).keySet()));
                }

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
        String normalized = sql.toUpperCase().replaceAll("\\s+", " ");

        // 检查是否是SHOW、DESCRIBE、EXPLAIN等也返回结果集的语句
        Pattern showPattern = Pattern.compile("^(SHOW|DESCRIBE|DESC|EXPLAIN)\\b");
        if (showPattern.matcher(normalized).find()) {
            return true;
        }

        // 检查是否是WITH开头的CTE查询
        if (normalized.startsWith("WITH ")) {
            return true;
        }

        // 检查是否是SELECT查询
        return normalized.startsWith("SELECT ");
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
}