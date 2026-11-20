package com.smartteach.live.config;

import com.smartteach.common.config.CorsProperties;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket at {@code /ws}, used only to push room states to browsers. Every action goes
 * through REST, so clients may only subscribe to a room topic. SEND frames are rejected: otherwise
 * anyone could broadcast a fake state to a room through the simple broker.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Pattern ROOM_TOPIC = Pattern.compile("/topic/live/\\d{6}");

    private final CorsProperties corsProperties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                StompCommand command = accessor.getCommand();
                if (StompCommand.SEND.equals(command)) {
                    throw new MessagingException("Sending is not allowed, use the REST API");
                }
                if (StompCommand.SUBSCRIBE.equals(command)) {
                    String destination = accessor.getDestination();
                    if (destination == null || !ROOM_TOPIC.matcher(destination).matches()) {
                        throw new MessagingException("Only /topic/live/{pin} can be subscribed");
                    }
                }
                return message;
            }
        });
    }
}
