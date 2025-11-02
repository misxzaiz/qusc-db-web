package com.dbadmin.service;

import com.dbadmin.model.AiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Java 11 HttpClient for true streaming
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    // 预定义的AI提供商配置
    private static final Map<String, AiProviderConfig> PROVIDERS = new HashMap<>();

    static {
        // DeepSeek
        PROVIDERS.put("deepseek", new AiProviderConfig(
            "https://api.deepseek.com",
            "deepseek-chat",
            "DeepSeek"
        ));

        // 心流 (Iflow)
        PROVIDERS.put("Iflow", new AiProviderConfig(
            "https://apis.iflow.cn/v1",
            "glm-4.6",
            "心流AI"
        ));

    }

    public String generateSql(String userInput, AiConfig config) throws Exception {
        if (!config.getEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new Exception("AI服务未配置或已禁用");
        }

        String prompt = buildSqlPrompt(userInput);

        Map<String, Object> requestBody;
        String url;

        // OpenAI兼容格式
        requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是一个专业的SQL助手，根据用户需求生成SQL语句。只返回SQL语句，不要包含任何解释。"));
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);

        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.put("max_tokens", config.getMaxTokens());
        }

        url = config.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );
            log.debug("AI服务返回: {}", response.getBody());

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content;

                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).path("message");
                    content = message.path("content").asText();
                } else {
                    throw new Exception("AI服务返回格式异常");
                }

                // 清理返回的SQL，移除可能的markdown标记
                return content.replaceAll("```sql", "").replaceAll("```", "").trim();
            } else {
                // 处理非200状态码
                String responseBody = response.getBody();
                if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    throw new Exception("API认证失败，请检查API Key是否正确");
                } else if (response.getStatusCode() == HttpStatus.FORBIDDEN) {
                    throw new Exception("API访问被拒绝，请检查权限或配额");
                } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    throw new Exception("API调用频率超限，请稍后再试");
                } else {
                    throw new Exception("AI服务返回错误: " + response.getStatusCode() + " - " + responseBody);
                }
            }
        } catch (Exception e) {
            // 改进错误信息
            String errorMessage = e.getMessage();
            if (errorMessage.contains("401") || errorMessage.contains("Unauthorized")) {
                throw new Exception("API认证失败，请检查API Key是否正确");
            } else if (errorMessage.contains("403") || errorMessage.contains("Forbidden")) {
                throw new Exception("API访问被拒绝，可能是权限不足或配额已用完");
            } else if (errorMessage.contains("429") || errorMessage.contains("Too Many Requests")) {
                throw new Exception("API调用频率超限，请稍后再试");
            } else if (errorMessage.contains("Connection")) {
                throw new Exception("无法连接到AI服务，请检查网络或API地址");
            }
            throw new Exception("调用AI服务失败: " + errorMessage);
        }
    }

    public String explainSql(String sql, AiConfig config) throws Exception {
        if (!config.getEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new Exception("AI服务未配置或已禁用");
        }

        String prompt = "请解释以下SQL语句的作用和执行逻辑：\n\n" + sql;

        Map<String, Object> requestBody;
        String url;

        // OpenAI兼容格式
        requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是一个SQL专家，请用简洁明了的语言解释SQL语句的作用。"));
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);

        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.put("max_tokens", config.getMaxTokens());
        }

        url = config.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content;

                if ("modelscope".equals(config.getProvider())) {
                    // ModelScope实际返回的是OpenAI兼容格式
                    JsonNode choices = root.path("choices");
                    if (choices.isArray() && choices.size() > 0) {
                        JsonNode message = choices.get(0).path("message");
                        content = message.path("content").asText();
                    } else {
                        // 备用：检查是否是旧格式
                        JsonNode output = root.path("output");
                        if (output != null && output.isArray() && output.size() > 0) {
                            content = output.get(0).path("text").asText();
                        } else {
                            throw new Exception("魔搭API返回格式异常");
                        }
                    }
                } else {
                    JsonNode choices = root.path("choices");
                    if (choices.isArray() && choices.size() > 0) {
                        JsonNode message = choices.get(0).path("message");
                        content = message.path("content").asText();
                    } else {
                        throw new Exception("AI服务返回格式异常");
                    }
                }

                return content;
            } else {
                String responseBody = response.getBody();
                if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    throw new Exception("API认证失败，请检查API Key是否正确");
                } else if (response.getStatusCode() == HttpStatus.FORBIDDEN) {
                    throw new Exception("API访问被拒绝，请检查权限或配额");
                } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    throw new Exception("API调用频率超限，请稍后再试");
                } else {
                    throw new Exception("AI服务返回错误: " + response.getStatusCode() + " - " + responseBody);
                }
            }
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage.contains("401") || errorMessage.contains("Unauthorized")) {
                throw new Exception("API认证失败，请检查API Key是否正确");
            } else if (errorMessage.contains("403") || errorMessage.contains("Forbidden")) {
                throw new Exception("API访问被拒绝，可能是权限不足或配额已用完");
            } else if (errorMessage.contains("429") || errorMessage.contains("Too Many Requests")) {
                throw new Exception("API调用频率超限，请稍后再试");
            } else if (errorMessage.contains("Connection")) {
                throw new Exception("无法连接到AI服务，请检查网络或API地址");
            }
            throw new Exception("调用AI服务失败: " + errorMessage);
        }
    }

    public String optimizeSql(String sql, AiConfig config) throws Exception {
        String prompt = "请优化以下SQL语句，提高其性能：\n\n" + sql +
            "\n\n请提供优化后的SQL语句，并简要说明优化点。";

        Map<String, Object> requestBody;
        String url;

        // OpenAI兼容格式
        requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是一个SQL性能优化专家，请提供SQL优化建议。"));
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);

        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.put("max_tokens", config.getMaxTokens());
        }

        url = config.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content;

                if ("modelscope".equals(config.getProvider())) {
                    // ModelScope实际返回的是OpenAI兼容格式
                    JsonNode choices = root.path("choices");
                    if (choices.isArray() && choices.size() > 0) {
                        JsonNode message = choices.get(0).path("message");
                        content = message.path("content").asText();
                    } else {
                        // 备用：检查是否是旧格式
                        JsonNode output = root.path("output");
                        if (output != null && output.isArray() && output.size() > 0) {
                            content = output.get(0).path("text").asText();
                        } else {
                            throw new Exception("魔搭API返回格式异常");
                        }
                    }
                } else {
                    JsonNode choices = root.path("choices");
                    if (choices.isArray() && choices.size() > 0) {
                        JsonNode message = choices.get(0).path("message");
                        content = message.path("content").asText();
                    } else {
                        throw new Exception("AI服务返回格式异常");
                    }
                }

                return content;
            } else {
                String responseBody = response.getBody();
                if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    throw new Exception("API认证失败，请检查API Key是否正确");
                } else if (response.getStatusCode() == HttpStatus.FORBIDDEN) {
                    throw new Exception("API访问被拒绝，请检查权限或配额");
                } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    throw new Exception("API调用频率超限，请稍后再试");
                } else {
                    throw new Exception("AI服务返回错误: " + response.getStatusCode() + " - " + responseBody);
                }
            }
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage.contains("401") || errorMessage.contains("Unauthorized")) {
                throw new Exception("API认证失败，请检查API Key是否正确");
            } else if (errorMessage.contains("403") || errorMessage.contains("Forbidden")) {
                throw new Exception("API访问被拒绝，可能是权限不足或配额已用完");
            } else if (errorMessage.contains("429") || errorMessage.contains("Too Many Requests")) {
                throw new Exception("API调用频率超限，请稍后再试");
            } else if (errorMessage.contains("Connection")) {
                throw new Exception("无法连接到AI服务，请检查网络或API地址");
            }
            throw new Exception("调用AI服务失败: " + errorMessage);
        }
    }

    public String generateCrud(String tableName, String columns, AiConfig config) throws Exception {
        if (!config.getEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new Exception("AI服务未配置或已禁用");
        }

        String prompt = String.format(
            "请为表 %s 生成完整的CRUD SQL语句。表结构如下：\n%s\n\n" +
            "请生成以下SQL语句：\n" +
            "1. CREATE TABLE (建表语句)\n" +
            "2. INSERT (插入语句)\n" +
            "3. SELECT (查询语句，支持条件查询)\n" +
            "4. UPDATE (更新语句)\n" +
            "5. DELETE (删除语句)\n\n" +
            "请使用markdown代码块格式返回，并添加必要的注释。",
            tableName, columns
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是一个SQL专家，请根据表结构生成完整的CRUD SQL语句。"));
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);

        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.put("max_tokens", config.getMaxTokens());
        }

        String url = config.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).path("message");
                    return message.path("content").asText();
                }
                throw new Exception("AI服务返回格式异常");
            } else {
                throw new Exception("AI服务返回错误: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new Exception("调用AI服务失败: " + e.getMessage());
        }
    }

    public String generateTestData(String tableName, String columns, int rowCount, AiConfig config) throws Exception {
        if (!config.getEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new Exception("AI服务未配置或已禁用");
        }

        String prompt = String.format(
            "请为表 %s 生成 %d 行测试数据。表结构如下：\n%s\n\n" +
            "要求：\n" +
            "1. 生成INSERT语句\n" +
            "2. 数据要真实、合理\n" +
            "3. 支持批量插入（一条INSERT插入多行）\n" +
            "4. 注意外键约束\n" +
            "5. 日期数据要使用MySQL支持的格式",
            tableName, rowCount, columns
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是一个数据生成专家，请根据表结构生成真实的测试数据。"));
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);

        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.put("max_tokens", config.getMaxTokens());
        }

        String url = config.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).path("message");
                    return message.path("content").asText();
                }
                throw new Exception("AI服务返回格式异常");
            } else {
                throw new Exception("AI服务返回错误: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new Exception("调用AI服务失败: " + e.getMessage());
        }
    }

    public String explainQueryPlan(String sql, String explainResult, AiConfig config) throws Exception {
        if (!config.getEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new Exception("AI服务未配置或已禁用");
        }

        String prompt = String.format(
            "请分析以下SQL语句的执行计划：\n\nSQL语句：\n%s\n\n执行计划：\n%s\n\n" +
            "请详细分析：\n" +
            "1. 执行步骤说明\n" +
            "2. 性能瓶颈识别\n" +
            "3. 索引使用情况\n" +
            "4. 优化建议",
            sql, explainResult
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是一个SQL性能分析专家，能够解读执行计划并提供优化建议。"));
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);

        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.put("max_tokens", config.getMaxTokens());
        }

        String url = config.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).path("message");
                    return message.path("content").asText();
                }
                throw new Exception("AI服务返回格式异常");
            } else {
                throw new Exception("AI服务返回错误: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new Exception("调用AI服务失败: " + e.getMessage());
        }
    }

    public String analyzeQueryResult(String sql, Map<String, Object> result, AiConfig config) throws Exception {
        if (!config.getEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new Exception("AI服务未配置或已禁用");
        }

        // 格式化查询结果用于分析
        String formattedResult = formatQueryResult(result);

        String prompt = String.format(
            "请分析以下SQL查询结果：\n\nSQL语句：\n%s\n\n查询结果（前10行）：\n%s\n\n" +
            "请提供以下分析：\n" +
            "1. 数据概览（总行数、主要统计信息）\n" +
            "2. 关键指标和趋势\n" +
            "3. 异常值或特殊模式识别\n" +
            "4. 数据质量评估\n" +
            "5. 可视化建议\n" +
            "6. 后续分析建议\n\n" +
            "请用markdown格式返回，包含表格和列表。",
            sql, formattedResult
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是一个数据分析专家，能够解读SQL查询结果并提供有价值的洞察。"));
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);

        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.put("max_tokens", config.getMaxTokens());
        }

        String url = config.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).path("message");
                    return message.path("content").asText();
                }
                throw new Exception("AI服务返回格式异常");
            } else {
                throw new Exception("AI服务返回错误: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new Exception("调用AI服务失败: " + e.getMessage());
        }
    }

    public String analyzeError(String sql, String error, AiConfig config) throws Exception {
        if (!config.getEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new Exception("AI服务未配置或已禁用");
        }

        String prompt = String.format(
            "请分析以下SQL错误并提供解决方案：\n\nSQL语句：\n%s\n\n错误信息：\n%s\n\n" +
            "请提供：\n" +
            "1. 错误原因分析\n" +
            "2. 具体修复步骤\n" +
            "3. 修复后的正确代码（用代码块显示）\n" +
            "4. 预防类似错误的建议\n" +
            "5. 相关SQL语法要点\n\n" +
            "请用markdown格式返回，重点突出修复方案。",
            sql, error
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是一个SQL专家，能够诊断SQL错误并提供实用的解决方案。"));
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);

        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.put("max_tokens", config.getMaxTokens());
        }

        String url = config.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).path("message");
                    return message.path("content").asText();
                }
                throw new Exception("AI服务返回格式异常");
            } else {
                throw new Exception("AI服务返回错误: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new Exception("调用AI服务失败: " + e.getMessage());
        }
    }

    private String formatQueryResult(Map<String, Object> result) {
        StringBuilder sb = new StringBuilder();

        // 添加列信息
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) result.get("columns");
        if (columns != null) {
            sb.append("列名：").append(String.join(", ", columns)).append("\n\n");
        }

        // 添加数据样本（前10行）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        if (data != null && !data.isEmpty()) {
            sb.append("数据样本（前10行）：\n");

            // 表头
            sb.append("| ");
            for (String col : columns) {
                sb.append(col).append(" | ");
            }
            sb.append("\n|");
            for (int i = 0; i < columns.size(); i++) {
                sb.append(" --- |");
            }
            sb.append("\n");

            // 数据行
            int rowCount = Math.min(10, data.size());
            for (int i = 0; i < rowCount; i++) {
                sb.append("| ");
                Map<String, Object> row = data.get(i);
                for (String col : columns) {
                    Object value = row.get(col);
                    sb.append(value != null ? value.toString() : "NULL").append(" | ");
                }
                sb.append("\n");
            }
        }

        // 添加总数信息
        Object totalCount = result.get("totalCount");
        if (totalCount != null) {
            sb.append("\n总行数：").append(totalCount);
        }

        return sb.toString();
    }

    // 流式聊天
    public void streamChat(String message, AiConfig config, String systemPrompt, List<Map<String, String>> history, ResponseBodyEmitter emitter) throws Exception {
        if (!config.getEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new Exception("AI服务未配置或已禁用");
        }

        log.info("开始流式聊天，消息: {}, 配置: {}", message, config.getName());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("stream", true);

        List<Map<String, String>> messages = new ArrayList<>();

        // 添加系统提示
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        } else {
            messages.add(Map.of("role", "system", "content", "你是一个友好的AI助手，能够回答各种问题。"));
        }

        // 添加历史记录
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
            log.info("添加历史记录: {} 条", history.size());
        }

        // 添加当前消息
        messages.add(Map.of("role", "user", "content", message));
        requestBody.put("messages", messages);

        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.put("max_tokens", config.getMaxTokens());
        }

        String url = config.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // 先发送一个初始事件，确保连接建立
        try {
            emitter.send("data: {\"status\": \"connected\"}\n\n");
        } catch (Exception e) {
            log.error("发送初始连接事件失败", e);
            return;
        }

        // 使用Java 11 HttpClient进行真正的流式处理
        CompletableFuture.runAsync(() -> {
            try {
                log.info("发送流式请求到: {}", url);
                log.debug("请求体: {}", objectMapper.writeValueAsString(requestBody));

                // 创建HTTP请求
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                        .build();

                // 发送请求并处理流式响应
                HttpResponse<java.io.InputStream> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );

                log.debug("AI流式响应状态: {}", response.statusCode());

                if (response.statusCode() == 200) {
                    try (java.io.InputStream inputStream = response.body();
                         java.io.BufferedReader reader = new java.io.BufferedReader(
                                 new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            // 处理SSE格式的每一行
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6).trim();

                                if (data.equals("[DONE]")) {
                                    emitter.send("data: {\"done\": true}\n\n");
                                    break;
                                }

                                // 解析JSON并发送内容
                                try {
                                    JsonNode jsonNode = objectMapper.readTree(data);
                                    JsonNode choices = jsonNode.path("choices");

                                    if (choices.isArray() && choices.size() > 0) {
                                        JsonNode delta = choices.get(0).path("delta");
                                        JsonNode content = delta.path("content");

                                        String contentText = content.asText();
                                        if (!contentText.isEmpty()) {
                                            log.debug("发送内容: {}", contentText);
                                            emitter.send("data: {\"content\": \"" + escapeJson(contentText) + "\"}\n\n");
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("解析JSON失败: {}", data);
                                }
                            }
                        }
                    }
                } else {
                    emitter.send("data: {\"error\": \"AI服务返回错误: " + response.statusCode() + "\"}\n\n");
                }
            } catch (Exception e) {
                log.error("流式聊天失败", e);
                try {
                    emitter.send("data: {\"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}\n\n");
                } catch (IOException ioException) {
                    log.error("发送错误失败", ioException);
                }
            } finally {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.debug("完成emitter失败", e);
                }
            }
        });
    }

    private boolean processStreamingResponseRealTime(String responseBody, SseEmitter emitter) {
        try {
            log.debug("处理流式响应，长度: {}", responseBody.length());

            // 按行处理SSE流
            String[] lines = responseBody.split("\n");
            boolean isCompleted = false;

            for (String line : lines) {
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                // 只处理data:开头的行
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();

                    // 检查结束标记
                    if (data.equals("[DONE]")) {
                        log.debug("收到流式结束标记");
                        try {
                            emitter.send(SseEmitter.event()
                                .name("end")
                                .data("{\"done\": true}"));
                            isCompleted = true;
                        } catch (Exception e) {
                            log.debug("发送结束标记失败，可能已关闭: {}", e.getMessage());
                        }
                        return true;
                    }

                    // 尝试解析JSON
                    try {
                        JsonNode jsonNode = objectMapper.readTree(data);
                        JsonNode choices = jsonNode.path("choices");

                        if (choices.isArray() && choices.size() > 0) {
                            JsonNode delta = choices.get(0).path("delta");
                            JsonNode content = delta.path("content");

                            String contentText = content.asText();
                            if (!contentText.isEmpty()) {
                                log.debug("发送内容: {}", contentText);
                                try {
                                    emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data("{\"content\": \"" + escapeJson(contentText) + "\"}"));
                                    // 添加小延迟，确保前端能接收
                                    Thread.sleep(10);
                                } catch (IllegalStateException e) {
                                    log.debug("Emitter已关闭，停止发送: {}", e.getMessage());
                                    isCompleted = true;
                                    break;
                                } catch (Exception e) {
                                    log.error("发送内容失败", e);
                                    // 发送失败也继续尝试，可能是临时问题
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("解析JSON失败: {} - {}", data, e.getMessage());
                    }
                }
            }

            // 如果没有提前结束，发送结束信号
            if (!isCompleted) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("end")
                        .data("{\"done\": true}"));
                } catch (Exception e) {
                    log.debug("发送最终结束标记失败: {}", e.getMessage());
                }
            }

            return isCompleted;

        } catch (Exception e) {
            log.error("处理流式响应失败", e);
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\": \"处理响应失败: " + e.getMessage() + "\"}"));
            } catch (IOException ioException) {
                log.error("发送错误失败", ioException);
            }
            return true; // 出错时也返回true，避免重复完成
        }
    }

    private void processStreamingResponse(String responseBody, SseEmitter emitter) {
        try {
            // SSE响应是逐行发送的，需要按行处理
            String[] lines = responseBody.split("\n");

            for (String line : lines) {
                line = line.trim();

                // 跳过空行
                if (line.isEmpty()) continue;

                // 处理SSE格式的数据
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);

                    // 检查结束标记
                    if (data.equals("[DONE]")) {
                        emitter.send(SseEmitter.event()
                            .name("end")
                            .data("{\"done\": true}"));
                        return;
                    }

                    // 解析JSON数据
                    try {
                        JsonNode jsonNode = objectMapper.readTree(data);
                        JsonNode choices = jsonNode.path("choices");

                        if (choices.isArray() && choices.size() > 0) {
                            JsonNode delta = choices.get(0).path("delta");
                            JsonNode content = delta.path("content");

                            if (!content.asText().isEmpty()) {
                                // 立即发送内容
                                emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data("{\"content\": \"" + escapeJson(content.asText()) + "\"}"));
                            }
                        }
                    } catch (Exception e) {
                        log.debug("解析SSE数据失败: " + data, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理流式响应失败", e);
        }
    }

    private void parseSseAndSend(String sseResponse, SseEmitter emitter) throws IOException {
        String[] lines = sseResponse.split("\n");
        StringBuilder contentBuffer = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("data: ")) {
                String data = line.substring(6);

                // 检查是否是结束标志
                if (data.equals("[DONE]")) {
                    // 发送完整内容
                    if (contentBuffer.length() > 0) {
                        String fullContent = contentBuffer.toString();
                        // 模拟逐字符发送
                        new Thread(() -> {
                            try {
                                for (int i = 0; i < fullContent.length(); i++) {
                                    emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data("{\"content\": \"" + escapeJson(fullContent.charAt(i)) + "\"}"));
                                    Thread.sleep(10); // 10ms延迟模拟流式效果
                                }
                                emitter.send(SseEmitter.event()
                                    .name("end")
                                    .data("{\"done\": true}"));
                            } catch (Exception e) {
                                // 忽略
                            }
                        }).start();
                    }
                    return;
                }

                // 解析JSON数据
                try {
                    JsonNode jsonNode = objectMapper.readTree(data);
                    JsonNode choices = jsonNode.path("choices");
                    if (choices.isArray() && choices.size() > 0) {
                        JsonNode delta = choices.get(0).path("delta");
                        JsonNode content = delta.path("content");
                        if (!content.asText().isEmpty()) {
                            contentBuffer.append(content.asText());
                        }
                    }
                } catch (Exception e) {
                    // 忽略解析错误，继续处理下一行
                    log.debug("解析SSE数据失败: " + data, e);
                }
            }
        }

        // 如果没有找到[DONE]标记，直接发送累积的内容
        if (contentBuffer.length() > 0) {
            String fullContent = contentBuffer.toString();
            new Thread(() -> {
                try {
                    for (int i = 0; i < fullContent.length(); i++) {
                        emitter.send(SseEmitter.event()
                            .name("message")
                            .data("{\"content\": \"" + escapeJson(fullContent.charAt(i)) + "\"}"));
                        Thread.sleep(10);
                    }
                    emitter.send(SseEmitter.event()
                        .name("end")
                        .data("{\"done\": true}"));
                } catch (Exception e) {
                    // 忽略
                }
            }).start();
        }
    }

    // 自由聊天
    public String freeChat(String message, AiConfig config, String systemPrompt, List<Map<String, String>> history) throws Exception {
        if (!config.getEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new Exception("AI服务未配置或已禁用");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();

        // 添加系统提示
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }

        // 添加历史记录
        if (history != null) {
            messages.addAll(history);
        }

        // 添加当前消息
        messages.add(Map.of("role", "user", "content", message));
        requestBody.put("messages", messages);

        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.put("max_tokens", config.getMaxTokens());
        }

        String url = config.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode messageNode = choices.get(0).path("message");
                    return messageNode.path("content").asText();
                }
                throw new Exception("AI服务返回格式异常");
            } else {
                throw new Exception("AI服务返回错误: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new Exception("调用AI服务失败: " + e.getMessage());
        }
    }

    private String extractContentFromResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).path("message");
            return message.path("content").asText();
        }
        throw new Exception("无法提取响应内容");
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private String escapeJson(char c) {
        if (c == '"') return "\\\"";
        if (c == '\\') return "\\\\";
        if (c == '\n') return "\\n";
        if (c == '\r') return "\\r";
        if (c == '\t') return "\\t";
        return String.valueOf(c);
    }

    private String buildSqlPrompt(String userInput) {
        return String.format(
            "根据以下需求生成SQL语句：\n%s\n\n注意：\n1. 只返回SQL语句\n2. 使用标准SQL语法\n3. 假设表名和字段名使用英文",
            userInput
        );
    }


    public static class AiProviderConfig {
        private String baseUrl;
        private String defaultModel;
        private String displayName;

        public AiProviderConfig(String baseUrl, String defaultModel, String displayName) {
            this.baseUrl = baseUrl;
            this.defaultModel = defaultModel;
            this.displayName = displayName;
        }

        public String getBaseUrl() { return baseUrl; }
        public String getDefaultModel() { return defaultModel; }
        public String getDisplayName() { return displayName; }
    }
}