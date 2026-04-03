package com.example.demo.Mapper;

import com.example.demo.Dto.User.UserData;
import com.example.demo.Dto.User.UserdataDetails;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;

import java.util.*;

@Mapper
public interface UserMapper {

    @MapKey("username")
    Map<String, Map<String, Object>> select(UserData user);

    void create(UserData user);

    void delete(UserData userData);

    void createUserdataDetail(UserdataDetails userdataDetail);

    @MapKey("username")
    Map<String, Map<String, Object>> selectUserdataDetail(UserdataDetails userdataDetails);

    void updateUserdataDetail(UserdataDetails userdataDetail);

    void deleteUserdataDetail(UserdataDetails userdataDetails);

    List<String> queryUserName();

    void updateUserdataDetailIsActive(String username);
}
