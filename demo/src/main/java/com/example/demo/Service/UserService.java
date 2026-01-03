package com.example.demo.Service;

import java.util.Map;

public interface UserService {

    Map<String, Object> register(Map<String, Object> map);

    String login(Map<String, Object> map);

    boolean validateToken(String token);

    void logout(String token);
}
