package com.aiinterview.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置
 *
 * 配置 STOMP over WebSocket
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，前缀 /topic（广播）和 /queue（点对点）
        config.enableSimpleBroker("/topic", "/queue");

        // 客户端发送消息的前缀
        config.setApplicationDestinationPrefixes("/app");

        // 用户消息前缀（用于点对点消息）
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 STOMP 端点，使用 SockJS 作为 fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // 生产环境请配置具体域名
                .withSockJS();
    }
}
