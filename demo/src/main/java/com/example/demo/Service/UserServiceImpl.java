package com.example.demo.Service;

import com.example.demo.Mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    @CachePut(value = "user", key = "#map['name']")
    public boolean createUser(Map<String, Object> map) {
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
    @Cacheable(value = "user", key = "#map['name']")
    public Map<String, Object> query(Map<String, Object> map) {
        List<Object> userList = userMapper.select(map);
        if (userList.isEmpty()) {
            return Collections.emptyMap();
        };

        Map<String, Object> userMap = (Map<String, Object>) userList.get(0);

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
    @CacheEvict(
            value = "user",
            key = "#map['name']",
            beforeInvocation = true
    )
    public int delete(Map<String, Object> map) {
        return userMapper.delete(map);
    }
}
