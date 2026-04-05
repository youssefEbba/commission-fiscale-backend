package mr.gov.finances.sgci.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = CorsAllowedOrigins.FRONTEND.toArray(String[]::new);
        // STOMP.js / @stomp/stompjs en WebSocket native : wss://hôte/ws?token=…
        registry.addEndpoint("/ws").setAllowedOrigins(origins);
        // Navigateur / fallback transport : SockJS (/ws/info puis …/websocket)
        registry.addEndpoint("/ws").setAllowedOrigins(origins).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
