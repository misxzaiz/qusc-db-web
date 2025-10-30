package com.dbadmin.controller;

import com.dbadmin.service.ConnectionManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<?> executeUpdate(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String sql = request.get("sql");

        if (sessionId == null || sql == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId and sql are required"));
        }

        try {
            int affectedRows = connectionManager.executeUpdate(sessionId, sql);
            return ResponseEntity.ok(Map.of("affectedRows", affectedRows));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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