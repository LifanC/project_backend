package com.example.demo.Mapper;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface SecretMapper {

    Integer getSecret();

    void createSecret(String secretNum);

    String getSecretOnly();
}
