package com.example.demo.Config.WebSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

public class UserHandshakeHandler extends DefaultHandshakeHandler {

    private final Logger logger = LoggerFactory.getLogger(UserHandshakeHandler.class);

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;

        String username = servletRequest.getServletRequest().getParameter("username");

        logger.info("Handshake username = " + username);

        return () -> username;
    }
}
