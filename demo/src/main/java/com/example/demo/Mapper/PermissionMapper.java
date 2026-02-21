package com.example.demo.Mapper;

import com.example.demo.Dto.Permissions.Permission;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface PermissionMapper {

    @MapKey("username")
    Map<String, Map<String, Object>> select(Permission permission);

    void create(Permission permission);

    void update(Permission permission);

    void delete(Permission permission);

    List<String> selectAll();
}
