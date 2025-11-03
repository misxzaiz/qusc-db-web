package com.dbadmin.controller;

import com.dbadmin.model.ConnectionInfo;
import com.dbadmin.service.ConnectionManagerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    @Autowired
    private ConnectionManagerService connectionManager;

    @PostMapping("/connect")
    public ResponseEntity<Map<String, String>> connect(@Valid @RequestBody ConnectionInfo connectionInfo) {
        try {
            String sessionId = connectionManager.createConnection(connectionInfo);
            return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "message", "Connected successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to connect: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(@RequestBody ConnectionInfo connectionInfo) {
        try {
            boolean success = connectionManager.testConnection(connectionInfo);
            return ResponseEntity.ok(Map.of("success", success));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/{sessionId}/disconnect")
    public ResponseEntity<Map<String, String>> disconnect(@PathVariable String sessionId) {
        connectionManager.closeConnection(sessionId);
        return ResponseEntity.ok(Map.of("message", "Disconnected"));
    }

    @GetMapping("/{sessionId}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String sessionId) {
        boolean isValid = connectionManager.isValidConnection(sessionId);
        ConnectionInfo info = connectionManager.getConnectionInfo(sessionId);

        if (info == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
            "valid", isValid,
            "connection", Map.of(
                "name", info.getName(),
                "host", info.getHost(),
                "port", info.getPort(),
                "database", info.getDatabase(),
                "username", info.getUsername()
            )
        ));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> getSessions() {
        // This would require exposing the active connections from ConnectionManagerService
        // For now, return empty list as the sessions are managed in frontend
        return ResponseEntity.ok(Collections.emptyList());
    }
}