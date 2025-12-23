package com.example.demo.Service;

import com.example.demo.Mapper.UserMapper;
import com.example.demo.redis.RedisService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisService redisService;

    @Override
    public boolean createUser(String name, String data) {
        redisService.set(name, data);
        boolean judge = true;
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("name", name);
            map.put("data", data);
            Object user = userMapper.select(map);
            if (user == null) {
                userMapper.create(map);
            } else {
                userMapper.update(map);
            }
        } catch (Exception e) {
            judge = false;
        }
        return judge;
    }

    @Override
    public Object query(String name) {
        Object user = redisService.get(name);
        if (user == null) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", name);
            user = userMapper.select(map);
        }
        return user;
    }

    @Override
    public int delete(String name) {
        Object user = redisService.get(name);
        if (user != null) {
            redisService.delete(name);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        return userMapper.delete(map);
    }
}
