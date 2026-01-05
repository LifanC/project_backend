package com.example.demo.Service;

import java.util.Map;

public interface UserService {

    Map<String, Object> register(Map<String, Object> map);

    Map<String, Object> updatePassword(Map<String, Object> map);

    Map<String, Object> login(Map<String, Object> map);

    Map<String, Object> validateToken(String token);

    Map<String, Object> query(Map<String, Object> map);

    Map<String, Object> logout(String token);
}
