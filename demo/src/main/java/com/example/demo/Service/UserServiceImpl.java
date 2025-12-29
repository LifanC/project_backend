package com.example.demo.Service;

import com.example.demo.Mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    @CachePut(value = "user", key = "#map['name']")
    public Map<String, Object> createUser(Map<String, Object> map) {
        Date date = new Date();
        LocalDate localDate = date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        int rocYear = localDate.getYear() - 1911;
        String result = String.format(
                "%03d%02d%02d",
                rocYear,
                localDate.getMonthValue(),
                localDate.getDayOfMonth()
        );
        map.put("update_date", result);
        map.put("timestamp", date);
        try {
            userMapper.create(map);
        } catch (Exception e) {
            userMapper.update(map);
        }
        return map;
    }

    @Override
    @Cacheable(value = "user", key = "#map['name']")
    public Map<String, Object> query(Map<String, Object> map) {
        List<Object> userList = userMapper.select(map);
        if (userList.isEmpty()) {
            return Collections.emptyMap();
        };
        return (Map<String, Object>) userList.getFirst();
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
