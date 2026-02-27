package com.aiinterview.model.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class OpenAiRequest {
    private String model;
    private List<OpenAiMessage> messages;
    private Double temperature;
    
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    
    private Boolean stream;
    
    @JsonProperty("response_format")
    private Map<String, Object> responseFormat;

    public OpenAiRequest() {
        this.model = "gpt-3.5-turbo";
        this.temperature = 0.7;
        this.maxTokens = 1000;
        this.stream = false;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<OpenAiMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<OpenAiMessage> messages) {
        this.messages = messages;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }
    
    public Map<String, Object> getResponseFormat() {
        return responseFormat;
    }
    
    public void setResponseFormat(Map<String, Object> responseFormat) {
        this.responseFormat = responseFormat;
    }
}

