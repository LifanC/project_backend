package com.example.demo.Mapper;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface RoleMapper {
    List<String> getRoles();

    void createUserRole(String username, Integer roleId);

    @MapKey("username")
    Map<String, Map<String, Object>> selectUserRole(String username);

    @MapKey("id")
    List<Map<String, Object>> getRolesId();

    void updateUserRole(String username, Integer roleIdOld, Integer roleId);

    void deleteUserRole(String username);

    List<String> getSelectRoles(String username);

    @MapKey("code")
    Map<String, Map<String, Object>> selectPermissionsCode(String allowedRoles);
}
