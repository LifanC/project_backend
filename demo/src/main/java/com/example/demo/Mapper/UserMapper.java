package com.example.demo.Mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.*;

@Mapper
public interface UserMapper {

    Map<String, Object> select(Map<String, Object> map);

    void create(Map<String, Object> map);

    void update(Map<String, Object> map);

    void updateToken(Map<String, Object> map);

    Map<String, Object> selectUsername(String token);

    Map<String, Object> selectToken(String username);
}
