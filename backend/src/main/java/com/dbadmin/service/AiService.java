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

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
            "Iflow-chat",
            "心流AI"
        ));

        // OpenAI
        PROVIDERS.put("openai", new AiProviderConfig(
            "https://api.openai.com/v1",
            "gpt-3.5-turbo",
            "OpenAI"
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