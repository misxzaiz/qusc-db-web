package com.dbadmin.model;

public class AiConfig {
    private String id;
    private String name;
    private String provider; // deepseek, modelscope, Iflow, openai, custom
    private String apiKey;
    private String baseUrl;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private Boolean enabled;

    public AiConfig() {}

    public AiConfig(String id, String name, String provider, String apiKey, String baseUrl, String model) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.temperature = 0.7;
        this.maxTokens = 4000;
        this.enabled = true;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}