package com.example.demo.Mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.*;

@Mapper
public interface UserMapper {

    void create(Map<String, Object> map);

    void update(Map<String, Object> map);

    List<Object> select(Map<String, Object> map);

    int delete(Map<String, Object> map);

}
