package com.example.demo.Service;

import java.util.List;
import java.util.Map;

public interface UserService {

    boolean createUser(Map<String, Object> map);

    Map<String, Object> query(Map<String, Object> map);

    int delete(Map<String, Object> map);
}
