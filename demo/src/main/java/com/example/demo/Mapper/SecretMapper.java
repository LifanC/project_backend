package com.example.demo.Mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SecretMapper {

    Integer getSecret();

    void createSecret(String secretNum);

    String getSecretOnly();
}
