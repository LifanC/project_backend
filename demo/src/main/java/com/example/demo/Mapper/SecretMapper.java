package com.example.demo.Mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface SecretMapper {

    void delNoSecret(String dateFormat);

    List<Map<String, Object>> getSecret(String dateFormatStr);

    void createSecret(String secretNum, String dateFormat);

    Map<String, Object> getSecretOnly(String dateFormat);
}
