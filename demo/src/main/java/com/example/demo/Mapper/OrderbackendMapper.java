package com.example.demo.Mapper;

import com.example.demo.Dto.Orderbackend.UserUser;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderbackendMapper {
    List<Map<String, Object>> selectUserUser(UserUser userUser);
}
