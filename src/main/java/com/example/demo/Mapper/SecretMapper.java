package com.example.demo.Mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SecretMapper {

    boolean getSecret();

    void createSecret(String secretNum);

    String getSecretOnly();
}
