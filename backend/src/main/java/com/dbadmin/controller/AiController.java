package com.dbadmin.controller;

import com.dbadmin.service.AiService;
import com.dbadmin.model.AiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:3000")
public class AiController {

    @Autowired
    private AiService aiService;

    // 临时存储配置（实际应该使用数据库）
    private Map<String, AiConfig> configs = new HashMap<>();

    @PostMapping("/generate-sql")
    public ResponseEntity<?> generateSql(@RequestBody Map<String, String> request) {
        String userInput = request.get("input");
        String configId = request.get("configId");

        if (userInput == null || userInput.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "输入不能为空"));
        }

        try {
            AiConfig config = getConfig(configId);
            String sql = aiService.generateSql(userInput, config);
            return ResponseEntity.ok(Map.of("sql", sql));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/explain-sql")
    public ResponseEntity<?> explainSql(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        String configId = request.get("configId");

        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "SQL语句不能为空"));
        }

        try {
            AiConfig config = getConfig(configId);
            String explanation = aiService.explainSql(sql, config);
            return ResponseEntity.ok(Map.of("explanation", explanation));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/optimize-sql")
    public ResponseEntity<?> optimizeSql(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        String configId = request.get("configId");

        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "SQL语句不能为空"));
        }

        try {
            AiConfig config = getConfig(configId);
            String optimized = aiService.optimizeSql(sql, config);
            return ResponseEntity.ok(Map.of("optimized", optimized));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/providers")
    public ResponseEntity<?> getProviders() {
        Map<String, Object> providers = new HashMap<>();
        providers.put("deepseek", Map.of(
            "name", "DeepSeek",
            "baseUrl", "https://api.deepseek.com",
            "models", new String[]{"deepseek-chat", "deepseek-coder"}
        ));
        providers.put("Iflow", Map.of(
            "name", "心流",
            "baseUrl", "https://apis.iflow.cn/v1",
            "models", new String[]{"Iflow-chat", "Iflow-pro"}
        ));
        providers.put("openai", Map.of(
            "name", "OpenAI",
            "baseUrl", "https://api.openai.com/v1",
            "models", new String[]{"gpt-3.5-turbo", "gpt-4", "gpt-4-turbo-preview"}
        ));
        providers.put("custom", Map.of(
            "name", "自定义",
            "baseUrl", "",
            "models", new String[]{""}
        ));

        return ResponseEntity.ok(providers);
    }

    @PostMapping("/config")
    public ResponseEntity<?> saveConfig(@RequestBody AiConfig config) {
        if (config.getId() == null || config.getId().isEmpty()) {
            config.setId(String.valueOf(System.currentTimeMillis()));
        }

        configs.put(config.getId(), config);
        return ResponseEntity.ok(Map.of("success", true, "id", config.getId()));
    }

    @GetMapping("/configs")
    public ResponseEntity<?> getConfigs() {
        return ResponseEntity.ok(configs.values());
    }

    @DeleteMapping("/config/{id}")
    public ResponseEntity<?> deleteConfig(@PathVariable String id) {
        configs.remove(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/config/{id}/test")
    public ResponseEntity<?> testConfig(@PathVariable String id) {
        try {
            AiConfig config = configs.get(id);
            if (config == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "配置不存在"));
            }
            // 测试连接
            String testResult = aiService.generateSql("SELECT 1", config);
            return ResponseEntity.ok(Map.of("success", true, "message", "连接成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "连接失败: " + e.getMessage()));
        }
    }

    private AiConfig getConfig(String configId) throws Exception {
        if (configId != null && configs.containsKey(configId)) {
            return configs.get(configId);
        }
        // 返回第一个启用的配置
        return configs.values().stream()
            .filter(AiConfig::getEnabled)
            .findFirst()
            .orElseThrow(() -> new Exception("没有可用的AI配置"));
    }
}