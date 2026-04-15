package com.example.demo.Service;

import com.example.demo.Common.ConvertFormat;
import com.example.demo.Common.RedisKey;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Permissions.PermissionRequest;
import com.example.demo.Dto.Permissions.Permission;
import com.example.demo.Dto.User.UserData;
import com.example.demo.Dto.User.UserdataDetails;
import com.example.demo.Exception.*;
import com.example.demo.Mapper.PermissionMapper;
import com.example.demo.Mapper.RoleMapper;
import com.example.demo.Mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final Logger logger = LoggerFactory.getLogger(PermissionServiceImpl.class);

    // ? redis 到期時間 Seconds
    @Value("${jwt.expirationSeconds}")
    private long expirationSeconds;

    private final PermissionMapper permissionMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public PermissionServiceImpl(
            PermissionMapper permissionMapper,
            UserMapper userMapper,
            RoleMapper roleMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.permissionMapper = permissionMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /*
     * 防 Cache Stampede（雪崩）
     * 問題：* 大量 key 同時過期 → DB 被打爆
     * */
    private int expirationSecondsAddRndomNumber() {
        int min = 1;
        int max = 60;
        return Math.toIntExact(expirationSeconds + (new Random().nextInt((max - min) + 1) + min));
    }

    // 單次回源鎖
    private final ReentrantLock lock = new ReentrantLock();

    private Map<String, Object> getPermission(Permission permission) {
        return permissionMapper.select(permission).get(permission.getUsername());
    }

    private boolean validatePermission(String permissions) {
        return roleMapper.getRoles().stream().anyMatch(role -> role.equals(permissions));
    }

    private Map<String, Integer> getRoleId() {
        Map<String, Integer> roleId = new HashMap<>();
        List<Map<String, Object>> roleList = roleMapper.getRolesId();
        for (Map<String, Object> role : roleList) {
            Integer id = Integer.parseInt(role.get("id").toString());
            roleId.put(role.get("name").toString(), id);
        }
        return roleId;
    }

    @Override
    public ResponseEntity<?> testLogin() {
        logger.info("permissions/testLogin: Permissions is working!");
        List<Object> messageList = List.of("Permissions is working!");
        Map<String, Map<Integer, Object>> message = Map.of(
                "content", ConvertFormat.convert(messageList)
        );
        HttpStatus status = HttpStatus.OK;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.api(
                        status,
                        message
                ));
    }

    @Override
    @Transactional
    public ResponseEntity<?> register(PermissionRequest request) {
        final String username = request.getUsername().trim();
        final String password = request.getPassword().trim();
        final String permissions = request.getPermissions().trim().toUpperCase();
        boolean validRole = validatePermission(permissions);
        if (!validRole) {
            throw new BadRequestException(username + " - 註冊權限錯誤");
        }
        Permission permission = new Permission(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Permission register 拿鎖");
                try {
                    try {
                        permission.setPassword(passwordEncoder.encode(password));
                        permission.setPermissions(permissions);
                        permissionMapper.create(permission);
                        logger.info("Permission 註冊權限帳號成功，username={}, permissions={}", username, permissions);

                        UserData userData = new UserData(username);
                        userMapper.create(userData);
                        logger.info("UserData 註冊權限帳號成功，username={}", username);
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String jsonMap = objectMapper.writeValueAsString(userData);
                        stringRedisTemplate.opsForValue().set(
                                userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        String permissionsAllKey =
                                RedisKey.redisPermissionsKey.get("permissionsAll").replace("{1}", "*");
                        stringRedisTemplate.delete(permissionsAllKey);

                        roleMapper.createUserRole(username, getRoleId().get(permissions));

                        Map<String, Object> permissionsSelect = getPermission(permission);
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + permissions,
                                username + " - 註冊權限帳號成功",
                                "新增日期" + ((Timestamp) permissionsSelect.get("created_date")).toLocalDateTime()
                        );
                        Map<String, Map<Integer, Object>> message = Map.of(
                                "content", ConvertFormat.convert(messageList)
                        );
                        HttpStatus status = HttpStatus.CREATED;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        message
                                ));
                    } catch (DuplicateKeyException e) {
                        logger.warn("Permission 註冊失敗，註冊權限帳號已存在，username={}", username);
                        throw new ResourceAlreadyExistsException(username + " - 註冊權限帳號已存在", e);
                    } catch (DataIntegrityViolationException e) {
                        logger.warn("Permission 註冊失敗，註冊權限帳號資料不合法，username={}", username);
                        throw new IsViolationException(username + " - 註冊權限帳號資料不合法", e);
                    } catch (DataAccessException e) {
                        logger.error("Permission 資料庫錯誤，username={}", username);
                        throw new DBException(username + " - 系統錯誤，請稍後再試", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : register 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號 -" + username,
                        "權限 -" + permissions,
                        username + " - 註冊，資源忙碌，請重試"
                );
                Map<String, Map<Integer, Object>> message = Map.of(
                        "content", ConvertFormat.convert(messageList)
                );
                HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
                return ResponseEntity
                        .status(status)
                        .body(ApiResponse.api(
                                status,
                                message
                        ));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional
    public ResponseEntity<?> query() {
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Permission query 拿鎖");
                try {
                    List<String> permissionsSelect;
                    String permissionsAllKey =
                            RedisKey.redisPermissionsKey.get("permissionsAll").replace("{1}", "*");
                    String json = stringRedisTemplate.opsForValue().get(permissionsAllKey);
                    if (json != null) {
                        permissionsSelect = objectMapper.readValue(json, new TypeReference<>() {});
                    } else {
                        permissionsSelect = permissionMapper.selectAll();
                        String jsonMap = objectMapper.writeValueAsString(permissionsSelect);
                        stringRedisTemplate.opsForValue().set(
                                permissionsAllKey, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                    }
                    if (permissionsSelect.isEmpty()) {
                        throw new ResourceNotFoundException("查詢帳號不存在");
                    }
                    logger.info("Permission 帳號查詢成功");
                    List<Object> messageList = List.of(
                            "帳號查詢成功",
                            permissionsSelect
                    );
                    Map<String, Map<Integer, Object>> message = Map.of(
                            "content", ConvertFormat.convert(messageList)
                    );
                    HttpStatus status = HttpStatus.OK;
                    return ResponseEntity
                            .status(status)
                            .body(ApiResponse.api(
                                    status,
                                    message
                            ));
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("query 資源忙碌，請重試");
                Map<String, Map<Integer, Object>> message = Map.of(
                        "content", ConvertFormat.convert(List.of("查詢，資源忙碌，請重試"))
                );
                HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
                return ResponseEntity
                        .status(status)
                        .body(ApiResponse.api(
                                status,
                                message
                        ));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional
    public ResponseEntity<?> update(PermissionRequest request) {
        final String username = request.getUsername().trim();
        final String password = request.getPassword().trim();
        final String permissions = request.getPermissions().trim().toUpperCase();
        boolean validRole = validatePermission(permissions);
        if (!validRole) {
            throw new BadRequestException(username + " - 更改權限錯誤");
        }
        Permission permission = new Permission(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Permission update 拿鎖");
                try {
                    Map<String, Object> permissionsSelect = getPermission(permission);
                    if (permissionsSelect == null) {
                        logger.error("Permission 沒有找到要更改的使用者");
                        throw new ResourceNotFoundException(username + " - 沒有找到要更改的使用者");
                    }
                    String userPassword = permissionsSelect.get("password").toString();
                    if (!passwordEncoder.matches(password, userPassword)) {
                        logger.error("Permission 更改密碼錯誤");
                        throw new ResourceNotFoundException(username + " - 更改密碼錯誤");
                    }
                    permission.setPermissions(permissions);
                    permissionMapper.update(permission);
                    logger.info("Permission 已更改權限");
                    Map<String, Integer> roleId = getRoleId();
                    Map<String, Object> userRole = roleMapper.selectUserRole(username).get(username);
                    Integer roleIdOld = Integer.parseInt(userRole.get("role_id").toString());
                    roleMapper.updateUserRole(username, roleIdOld, roleId.get(permissions));
                    permissionsSelect = getPermission(permission);
                    List<Object> messageList = List.of(
                            "帳號 - " + username,
                            "權限 - " + permissions,
                            username + " - 已更改權限",
                            "新增日期" + ((Timestamp) permissionsSelect.get("created_date")).toLocalDateTime(),
                            "更改日期" + ((Timestamp) permissionsSelect.get("updated_date")).toLocalDateTime()
                    );
                    Map<String, Map<Integer, Object>> message = Map.of(
                            "content", ConvertFormat.convert(messageList)
                    );
                    HttpStatus status = HttpStatus.OK;
                    return ResponseEntity
                            .status(status)
                            .body(ApiResponse.api(
                                    status,
                                    message
                            ));
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : update 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號 -" + username,
                        "權限 -" + permissions,
                        username + " - 更改權限，資源忙碌，請重試"
                );
                Map<String, Map<Integer, Object>> message = Map.of(
                        "content", ConvertFormat.convert(messageList)
                );
                HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
                return ResponseEntity
                        .status(status)
                        .body(ApiResponse.api(
                                status,
                                message
                        ));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional
    public ResponseEntity<?> delete(final String username, final String password) {
        Permission permission = new Permission(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Permission delete 拿鎖");
                try {
                    Map<String, Object> permissionsSelect = getPermission(permission);
                    if (permissionsSelect == null) {
                        logger.error("Permission 沒有找到要刪除的帳號");
                        throw new ResourceNotFoundException(username + " - 沒有找到要刪除的帳號");
                    }
                    String userPassword = permissionsSelect.get("password").toString();
                    if (!passwordEncoder.matches(password, userPassword)) {
                        logger.error("Permission 刪除帳號密碼錯誤");
                        throw new ResourceNotFoundException(username + " - 刪除帳號密碼錯誤");
                    }
                    permissionMapper.delete(permission);

                    UserData userData = new UserData(username);
                    userMapper.delete(userData);
                    String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                    stringRedisTemplate.delete(userOnly);
                    String permissionsAllKey =
                            RedisKey.redisPermissionsKey.get("permissionsAll").replace("{1}", "*");
                    stringRedisTemplate.delete(permissionsAllKey);

                    UserdataDetails userdataDetails = new UserdataDetails(username);
                    userMapper.deleteUserdataDetail(userdataDetails);
                    roleMapper.deleteUserRole(username);

                    logger.info("Permission 帳號已刪除");
                    String permissions = permissionsSelect.get("permissions").toString();
                    List<Object> messageList = List.of(
                            "帳號 - " + username,
                            "權限 - " + permissions,
                            username + " - 帳號已刪除",
                            "新增日期" + ((Timestamp) permissionsSelect.get("created_date")).toLocalDateTime(),
                            "更改日期" + ((Timestamp) permissionsSelect.get("updated_date")).toLocalDateTime()
                    );
                    Map<String, Map<Integer, Object>> message = Map.of(
                            "content", ConvertFormat.convert(messageList)
                    );
                    HttpStatus status = HttpStatus.OK;
                    return ResponseEntity
                            .status(status)
                            .body(ApiResponse.api(
                                    status,
                                    message
                            ));
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : delete 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號 -" + username,
                        username + " - 刪除權限，資源忙碌，請重試"
                );
                Map<String, Map<Integer, Object>> message = Map.of(
                        "content", ConvertFormat.convert(messageList)
                );
                HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
                return ResponseEntity
                        .status(status)
                        .body(ApiResponse.api(
                                status,
                                message
                        ));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
