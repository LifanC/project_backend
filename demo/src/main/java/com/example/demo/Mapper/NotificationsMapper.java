package com.example.demo.Mapper;

import com.example.demo.Dto.User.UserData;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface NotificationsMapper {

    List<Map<String, Object>> selectUserdataDetails();

    List<Map<String, Object>> selectQuotations(UserData userData);

}
