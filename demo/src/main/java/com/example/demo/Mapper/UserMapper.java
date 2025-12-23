package com.example.demo.Mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface UserMapper {

    void create(Map<String, Object> map);

    void update(Map<String, Object> map);

    Object select(Map<String, Object> map);

    void delete(Map<String, Object> map);

}
