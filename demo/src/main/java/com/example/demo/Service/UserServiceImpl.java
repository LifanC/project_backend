package com.example.demo.Service;

import com.example.demo.Mapper.UserMapper;
import com.example.demo.redis.RedisService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
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
    public boolean createUser(Map<String, Object> map) {
        redisService.set(map.get("name").toString(), map);
        boolean judge = true;
        try {
            userMapper.create(map);
        } catch (Exception e) {
            userMapper.update(map);
            judge = false;
        }
        return judge;
    }

    @Override
    public Object query(Map<String, Object> map) {
        Object user = redisService.get(map.get("name").toString());
        Map<String, Object> userMap = new HashMap<>();
        if (user != null) {
            userMap = (Map<String, Object>) user;
        } else {
            List<Object> userList = userMapper.select(map);
            if (!userList.isEmpty()) {
                userMap = (Map<String, Object>) userList.getFirst();
            }
        }
        if (userMap.isEmpty()) {
            return null;
        }
        return userMap.entrySet()
                .stream()
                .sorted((e1, e2) -> {
                    String name1 = e1.getKey();
                    String name2 = e2.getKey();
                    return name2.compareTo(name1); // DESC
                })
                .collect(
                        LinkedHashMap::new,  // 保留排序順序
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        LinkedHashMap::putAll
                );
    }

    @Override
    public int delete(Map<String, Object> map) {
        Object user = redisService.get(map.get("name").toString());
        if (user != null) {
            redisService.delete(map.get("name").toString());
        }
        return userMapper.delete(map);
    }
}
