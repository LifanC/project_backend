package com.example.demo.Service;

import com.example.demo.Common.CertificateFunction;
import com.example.demo.Dto.Permissions.PermissionRequest;
import com.example.demo.Dto.Permissions.Permission;
import com.example.demo.Dto.Permissions.PermissionResponse;
import com.example.demo.Dto.User.UserData;
import com.example.demo.Dto.User.UserdataDetails;
import com.example.demo.Exception.*;
import com.example.demo.Mapper.PermissionMapper;
import com.example.demo.Mapper.RoleMapper;
import com.example.demo.Mapper.UserMapper;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final Logger logger = LoggerFactory.getLogger(PermissionServiceImpl.class);

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RoleMapper roleMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final String keystorePath = "permission-keystorePath";

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
    @Transactional
    public ResponseEntity<?> register(PermissionRequest request) {
        final String username = request.getUsername().trim();
        final String password = request.getPassword().trim();
        final String permissions = request.getPermissions().trim().toUpperCase();
        boolean validRole = validatePermission(permissions);
        if (!validRole) {
            throw new BadRequestException("註冊權限錯誤");
        }
        Permission permission = new Permission(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Permission register 拿鎖");
                try {
//                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "register");
//                    if (!valid) {
//                        logger.error("register 憑證未通過");
//                        permission.setMessage("註冊憑證未通過");
//                        permission.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
//                        return ResponseEntity
//                                .status(permission.getStatus())
//                                .body(new PermissionResponse(permission));
//                    }
                    try {
                        permission.setPassword(passwordEncoder.encode(password));
                        permission.setPermissions(permissions);
                        permissionMapper.create(permission);
                        logger.info("Permission 註冊權限帳號成功，username={}, permissions={}", username, permissions);
                        UserData userData = new UserData(username);
                        userMapper.create(userData);
                        logger.info("UserData 註冊權限帳號成功，username={}", username);
                        Map<String, Object> permissionsSelect = getPermission(permission);
                        permission.setPermissions(permissionsSelect.get("permissions").toString());
                        permission.setMessage("註冊權限帳號成功");
                        permission.setStatus(HttpStatus.CREATED);
                        permission.setCreated_date(((Timestamp) permissionsSelect.get("created_date")).toLocalDateTime());
                        permission.setUpdated_date(((Timestamp) permissionsSelect.get("updated_date")).toLocalDateTime());
                        permission.setUsers(new ArrayList<>());

                        Map<String, Integer> roleId = getRoleId();
                        roleMapper.createUserRole(username, roleId.get(permissions));

                        return ResponseEntity
                                .status(permission.getStatus())
                                .body(new PermissionResponse(permission));
                    } catch (DuplicateKeyException e) {
                        logger.warn("Permission 註冊失敗，註冊權限帳號已存在，username={}", username);
                        throw new ResourceAlreadyExistsException("註冊權限帳號已存在", e);
                    } catch (DataIntegrityViolationException e) {
                        logger.warn("Permission 註冊失敗，註冊權限帳號資料不合法，username={}", username);
                        throw new IsViolationException("註冊權限帳號資料不合法", e);
                    } catch (DataAccessException e) {
                        logger.error("Permission 資料庫錯誤，username={}", username);
                        throw new DBException("系統錯誤，請稍後再試", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : register 資源忙碌，請重試", username);
                permission.setMessage("資源忙碌，請重試");
                permission.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(permission.getStatus())
                        .body(new PermissionResponse(permission));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public ResponseEntity<?> query() {
        Permission permission = new Permission("");
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Permission query 拿鎖");
                try {
//                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "query");
//                    if (!valid) {
//                        logger.error("query 憑證未通過");
//                        permission.setMessage("查詢憑證未通過");
//                        permission.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
//                        return ResponseEntity
//                                .status(permission.getStatus())
//                                .body(new PermissionResponse(permission));
//                    }
                    List<String> permissionsSelect = permissionMapper.selectAll();
                    if (permissionsSelect.isEmpty()) {
                        throw new ResourceNotFoundException("查詢帳號不存在");
                    }
                    permission.setUsername("");
                    permission.setPermissions("");
                    logger.info("Permission 帳號查詢成功");
                    permission.setMessage("帳號查詢成功");
                    permission.setStatus(HttpStatus.OK);
                    permission.setCreated_date(LocalDateTime.now());
                    permission.setUpdated_date(LocalDateTime.now());
                    permission.setUsers(permissionsSelect);

                    return ResponseEntity
                        .status(permission.getStatus())
                        .body(new PermissionResponse(permission));
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("query 資源忙碌，請重試");
                permission.setMessage("資源忙碌，請重試");
                permission.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(permission.getStatus())
                        .body(new PermissionResponse(permission));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public ResponseEntity<?> update(PermissionRequest request) {
        final String username = request.getUsername().trim();
        final String password = request.getPassword().trim();
        final String permissions = request.getPermissions().trim().toUpperCase();
        boolean validRole = validatePermission(permissions);
        if (!validRole) {
            throw new BadRequestException("更改權限錯誤");
        }
        Permission permission = new Permission(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Permission update 拿鎖");
                try {
//                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "update");
//                    if (!valid) {
//                        logger.error("update 憑證未通過");
//                        permission.setMessage("更改憑證未通過");
//                        permission.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
//                        return ResponseEntity
//                                .status(permission.getStatus())
//                                .body(new PermissionResponse(permission));
//                    }
                    Map<String, Object> permissionsSelect = getPermission(permission);
                    // 連動更改userdata表
                    if (permissionsSelect == null) {
                        logger.error("Permission 沒有找到要更改的使用者");
                        throw new ResourceNotFoundException("沒有找到要更改的使用者");
                    }
                    String userPassword = permissionsSelect.get("password").toString();
                    if (!passwordEncoder.matches(password, userPassword)) {
                        logger.error("Permission 更改密碼錯誤");
                        throw new ResourceNotFoundException("更改密碼錯誤");
                    }
                    permission.setPermissions(permissions);
                    permissionMapper.update(permission);
                    logger.info("Permission 已更改權限");
                    permission.setMessage("已更改權限");
                    permission.setStatus(HttpStatus.OK);
                    permissionsSelect = getPermission(permission);
                    permission.setCreated_date(((Timestamp) permissionsSelect.get("created_date")).toLocalDateTime());
                    permission.setUpdated_date(((Timestamp) permissionsSelect.get("updated_date")).toLocalDateTime());
                    permission.setUsers(new ArrayList<>());

                    Map<String, Integer> roleId = getRoleId();
                    Map<String, Object> userRole = roleMapper.selectUserRole(username).get(username);
                    Integer roleIdOld = Integer.parseInt(userRole.get("role_id").toString());
                    roleMapper.updateUserRole(username, roleIdOld, roleId.get(permissions));

                    return ResponseEntity
                            .status(permission.getStatus())
                            .body(new PermissionResponse(permission));
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : update 資源忙碌，請重試", username);
                permission.setMessage("資源忙碌，請重試");
                permission.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(permission.getStatus())
                        .body(new PermissionResponse(permission));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public ResponseEntity<?> delete(final String username, final String password) {
        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
            throw new RuntimeException("刪除帳號密碼未輸入");
        }
        Permission permission = new Permission(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Permission delete 拿鎖");
                try {
//                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "delete");
//                    if (!valid) {
//                        logger.error("delete 憑證未通過");
//                        permission.setMessage("刪除憑證未通過");
//                        permission.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
//                        return ResponseEntity
//                                .status(permission.getStatus())
//                                .body(new PermissionResponse(permission));
//                    }
                    Map<String, Object> permissionsSelect = getPermission(permission);
                    if (permissionsSelect == null) {
                        logger.error("Permission 沒有找到要刪除的帳號");
                        throw new ResourceNotFoundException("沒有找到要刪除的帳號");
                    }
                    String userPassword = permissionsSelect.get("password").toString();
                    if (!passwordEncoder.matches(password, userPassword)) {
                        logger.error("Permission 刪除帳號密碼錯誤");
                        throw new ResourceNotFoundException("刪除帳號密碼錯誤");
                    }
                    permissionMapper.delete(permission);
                    UserData userData = new UserData(username);
                    userMapper.delete(userData);
                    UserdataDetails userdataDetails = new UserdataDetails(username);
                    userMapper.deleteUserdataDetail(userdataDetails);
                    userMapper.deleteUserdataDetailU(userdataDetails);
                    roleMapper.deleteUserRole(username);
                    logger.info("Permission 帳號已刪除");
                    String permissions = permissionsSelect.get("permissions").toString();
                    permission.setPermissions(permissions);
                    permission.setMessage("帳號已刪除");
                    permission.setStatus(HttpStatus.OK);
                    permission.setCreated_date(((Timestamp) permissionsSelect.get("created_date")).toLocalDateTime());
                    permission.setUpdated_date(((Timestamp) permissionsSelect.get("updated_date")).toLocalDateTime());
                    permission.setUsers(new ArrayList<>());

                    return ResponseEntity
                            .status(permission.getStatus())
                            .body(new PermissionResponse(permission));
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : delete 資源忙碌，請重試", username);
                permission.setMessage("資源忙碌，請重試");
                permission.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(permission.getStatus())
                        .body(new PermissionResponse(permission));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
