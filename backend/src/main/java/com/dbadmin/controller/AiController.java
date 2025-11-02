package com.dbadmin.controller;

import com.dbadmin.service.AiService;
import com.dbadmin.model.AiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:3000")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    @Autowired
    private AiService aiService;

    // 临时存储配置（实际应该使用数据库）
    private Map<String, AiConfig> configs = new HashMap<>();

    // AI角色预设
    private Map<String, Map<String, Object>> aiRoles = new ConcurrentHashMap<>();

    public AiController() {
        // 初始化默认角色
        initDefaultRoles();

        // 初始化默认测试配置
        initDefaultConfig();
    }

    private void initDefaultConfig() {
        AiConfig testConfig = new AiConfig();
        testConfig.setId("test");
        testConfig.setName("DeepSeek测试");
        testConfig.setProvider("openai");
        testConfig.setBaseUrl("https://api.deepseek.com");
        testConfig.setModel("deepseek-chat");
        testConfig.setApiKey("sk-your-api-key-here");
        testConfig.setEnabled(false); // 默认禁用，需要用户设置真实的API key
        configs.put("test", testConfig);
    }

    private void initDefaultRoles() {
        // SQL专家
        Map<String, Object> sqlExpert = new HashMap<>();
        sqlExpert.put("id", "sql-expert");
        sqlExpert.put("name", "SQL专家");
        sqlExpert.put("description", "专业的SQL开发和优化专家");
        sqlExpert.put("systemPrompt", "你是一位资深的SQL专家，拥有丰富的数据库设计和优化经验。");
        sqlExpert.put("avatar", "👨‍💻");
        aiRoles.put("sql-expert", sqlExpert);

        // 数据分析师
        Map<String, Object> dataAnalyst = new HashMap<>();
        dataAnalyst.put("id", "data-analyst");
        dataAnalyst.put("name", "数据分析师");
        dataAnalyst.put("description", "专业的数据分析和报告专家");
        dataAnalyst.put("systemPrompt", "你是一位专业的数据分析师，擅长从数据中发现洞察和模式。");
        dataAnalyst.put("avatar", "📊");
        aiRoles.put("data-analyst", dataAnalyst);

        // 助理
        Map<String, Object> assistant = new HashMap<>();
        assistant.put("id", "assistant");
        assistant.put("name", "智能助理");
        assistant.put("description", "友好的全能AI助理");
        assistant.put("systemPrompt", "你是一个友好、乐于助人的AI助理，能够回答各种问题。");
        assistant.put("avatar", "🤖");
        aiRoles.put("assistant", assistant);

        // 代码生成器
        Map<String, Object> codeGenerator = new HashMap<>();
        codeGenerator.put("id", "code-generator");
        codeGenerator.put("name", "代码生成器");
        codeGenerator.put("description", "专业的代码生成和优化专家");
        codeGenerator.put("systemPrompt", "你是一位专业的程序员，擅长生成高质量的代码和解决方案。");
        codeGenerator.put("avatar", "💻");
        aiRoles.put("code-generator", codeGenerator);
    }

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

    @PutMapping("/config/{id}")
    public ResponseEntity<?> updateConfig(@PathVariable String id, @RequestBody AiConfig config) {
        config.setId(id);
        configs.put(id, config);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/config/{id}")
    public ResponseEntity<?> deleteConfig(@PathVariable String id) {
        configs.remove(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/generate-crud")
    public ResponseEntity<?> generateCrud(@RequestBody Map<String, Object> request) {
        String tableName = (String) request.get("tableName");
        String columns = (String) request.get("columns");
        String configId = (String) request.get("configId");

        if (tableName == null || tableName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "表名不能为空"));
        }
        if (columns == null || columns.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "表结构不能为空"));
        }

        try {
            AiConfig config = getConfig(configId);
            String crud = aiService.generateCrud(tableName, columns, config);
            return ResponseEntity.ok(Map.of("crud", crud));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/generate-test-data")
    public ResponseEntity<?> generateTestData(@RequestBody Map<String, Object> request) {
        String tableName = (String) request.get("tableName");
        String columns = (String) request.get("columns");
        Integer rowCount = (Integer) request.getOrDefault("rowCount", 10);
        String configId = (String) request.get("configId");

        if (tableName == null || tableName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "表名不能为空"));
        }
        if (columns == null || columns.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "表结构不能为空"));
        }

        try {
            AiConfig config = getConfig(configId);
            String testData = aiService.generateTestData(tableName, columns, rowCount, config);
            return ResponseEntity.ok(Map.of("testData", testData));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/explain-query-plan")
    public ResponseEntity<?> explainQueryPlan(@RequestBody Map<String, Object> request) {
        String sql = (String) request.get("sql");
        String explainResult = (String) request.get("explainResult");
        String configId = (String) request.get("configId");

        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "SQL语句不能为空"));
        }
        if (explainResult == null || explainResult.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "执行计划不能为空"));
        }

        try {
            AiConfig config = getConfig(configId);
            String explanation = aiService.explainQueryPlan(sql, explainResult, config);
            return ResponseEntity.ok(Map.of("explanation", explanation));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/analyze-query-result")
    public ResponseEntity<?> analyzeQueryResult(@RequestBody Map<String, Object> request) {
        String sql = (String) request.get("sql");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) request.get("result");
        String configId = (String) request.get("configId");

        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "SQL语句不能为空"));
        }
        if (result == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "查询结果不能为空"));
        }

        try {
            AiConfig config = getConfig(configId);
            String analysis = aiService.analyzeQueryResult(sql, result, config);
            return ResponseEntity.ok(Map.of("analysis", analysis));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/analyze-error")
    public ResponseEntity<?> analyzeError(@RequestBody Map<String, Object> request) {
        String sql = (String) request.get("sql");
        String error = (String) request.get("error");
        String configId = (String) request.get("configId");

        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "SQL语句不能为空"));
        }
        if (error == null || error.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "错误信息不能为空"));
        }

        try {
            AiConfig config = getConfig(configId);
            String analysis = aiService.analyzeError(sql, error, config);
            return ResponseEntity.ok(Map.of("analysis", analysis));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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

    // 流式聊天API
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String message,
                                 @RequestParam(required = false) String configId,
                                 @RequestParam(required = false) String roleId) {
        log.info("收到流式聊天请求: message={}, configId={}, roleId={}", message, configId, roleId);

        SseEmitter emitter = new SseEmitter(60000L); // 增加超时时间到60秒

        // 设置超时和完成回调
        emitter.onTimeout(() -> {
            log.info("SSE连接超时");
            emitter.complete();
        });

        emitter.onCompletion(() -> {
            log.info("SSE连接完成");
        });

        // 在新线程中处理
        new Thread(() -> {
            try {
                // 获取AI配置
                AiConfig config = getConfig(configId);
                log.info("使用AI配置: {}", config.getName());

                // 获取角色信息
                String systemPrompt = null;
                if (roleId != null && aiRoles.containsKey(roleId)) {
                    systemPrompt = (String) aiRoles.get(roleId).get("systemPrompt");
                    log.info("使用角色: {}", roleId);
                }

                // 流式生成响应
                aiService.streamChat(message, config, systemPrompt, emitter);

            } catch (Exception e) {
                log.error("流式聊天处理失败", e);
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"error\": \"" + e.getMessage() + "\"}"));
                } catch (IOException ioException) {
                    log.error("发送错误事件失败", ioException);
                }
            }
        }).start();

        return emitter;
    }

    // 获取所有角色
    @GetMapping("/roles")
    public ResponseEntity<?> getRoles() {
        return ResponseEntity.ok(aiRoles.values());
    }

    // 创建自定义角色
    @PostMapping("/roles")
    public ResponseEntity<?> createRole(@RequestBody Map<String, Object> role) {
        if (!role.containsKey("name") || !role.containsKey("systemPrompt")) {
            return ResponseEntity.badRequest().body(Map.of("error", "角色名称和系统提示不能为空"));
        }

        String id = (String) role.get("id");
        if (id == null || id.isEmpty()) {
            id = "custom-" + System.currentTimeMillis();
            role.put("id", id);
        }

        role.put("isCustom", true);
        aiRoles.put(id, role);

        return ResponseEntity.ok(Map.of("success", true, "id", id));
    }

    // 更新角色
    @PutMapping("/roles/{id}")
    public ResponseEntity<?> updateRole(@PathVariable String id, @RequestBody Map<String, Object> role) {
        if (!aiRoles.containsKey(id)) {
            return ResponseEntity.notFound().build();
        }

        role.put("id", id);
        aiRoles.put(id, role);

        return ResponseEntity.ok(Map.of("success", true));
    }

    // 删除角色（仅允许删除自定义角色）
    @DeleteMapping("/roles/{id}")
    public ResponseEntity<?> deleteRole(@PathVariable String id) {
        Map<String, Object> role = aiRoles.get(id);
        if (role == null) {
            return ResponseEntity.notFound().build();
        }

        Boolean isCustom = (Boolean) role.getOrDefault("isCustom", false);
        if (!isCustom) {
            return ResponseEntity.badRequest().body(Map.of("error", "不能删除系统预设角色"));
        }

        aiRoles.remove(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // 自由聊天（不限制内容）
    @PostMapping("/chat/free")
    public ResponseEntity<?> freeChat(@RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        String configId = (String) request.get("configId");
        String roleId = (String) request.get("roleId");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) request.get("history");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "消息不能为空"));
        }

        try {
            AiConfig config = getConfig(configId);

            // 获取角色信息
            String systemPrompt = null;
            if (roleId != null && aiRoles.containsKey(roleId)) {
                systemPrompt = (String) aiRoles.get(roleId).get("systemPrompt");
            }

            String response = aiService.freeChat(message, config, systemPrompt, history);
            return ResponseEntity.ok(Map.of("response", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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