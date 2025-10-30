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
    public ResponseEntity<?> getTables(@PathVariable String sessionId) {
        try {
            List<String> tables = connectionManager.getTables(sessionId);
            return ResponseEntity.ok(Map.of("tables", tables));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}