package com.example.demo.Mapper;

import org.mybatis.spring.annotation.MapperScan;

import java.util.*;

@MapperScan
public interface UserMapper {

    Map<String, Object> select(Map<String, Object> map);

    void create(Map<String, Object> map);

    void delNoSecret(String dateFormat);

    List<Map<String, Object>> getSecret(String dateFormatStr);

    void createSecret(String secretNum, String dateFormat);

    Map<String, Object> getSecretOnly(String dateFormat);
}
