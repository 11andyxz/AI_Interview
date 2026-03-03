package com.aiinterview.ml.gateway;

import java.util.Map;

public class LlmRequest {
    private String endpoint;
    private String sessionId;
    private String roleId;
    private String level;
    private Map<String, Object> payload;

    public LlmRequest() {}

    public LlmRequest(String endpoint, String sessionId) {
        this.endpoint = endpoint;
        this.sessionId = sessionId;
    }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}
