package com.example.demo.Aspect;

import com.example.demo.Common.Context;
import com.example.demo.Common.RedisKey;
import com.example.demo.Dto.User.OrderRequest;
import com.example.demo.Dto.User.UserData;
import com.example.demo.Exception.ResourceNotFoundException;
import com.example.demo.Mapper.RoleMapper;
import com.example.demo.Mapper.UserMapper;
import com.example.demo.Security.Annotation.CheckRole;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Aspect
@Component
public class CommonAspect {

    private final Logger logger = LoggerFactory.getLogger(CommonAspect.class);

    // ? redis 到期時間 Seconds
    @Value("${jwt.expirationSeconds}")
    private long expirationSeconds;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public CommonAspect(
            UserMapper userMapper,
            RoleMapper roleMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

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

    private Map<String, Object> getUserData(UserData userData) {
        return userMapper.select(userData).get(userData.getUsername());
    }

    // ==========================
    // 權限檢查 (Around)
    // ==========================
    @Around("@annotation(checkRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint, CheckRole checkRole) throws Throwable {
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("checkRole 拿鎖");
                try {
                    OrderRequest req = (OrderRequest) joinPoint.getArgs()[0];
                    String username = req.getUsername();
                    UserData userData = new UserData(username);
                    String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                    String jsonUserOnly = stringRedisTemplate.opsForValue().get(userOnly);
                    Map<String, Object> userSelect;
                    if (jsonUserOnly != null) {
                        userSelect = objectMapper.readValue(jsonUserOnly, new TypeReference<>() {});
                    } else {
                        userSelect = getUserData(userData);
                        String jsonMap = objectMapper.writeValueAsString(userSelect);
                        stringRedisTemplate.opsForValue().set(
                                userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                    }
                    if (userSelect == null) {
                        logger.error("{} - 帳號不存在", username);
                        throw new ResourceNotFoundException(username + " - 帳號不存在");
                    }
                    String userRole = RedisKey.redisCommonAspectKey.get("userRole").replace("{1}", username);
                    String jsonRole = stringRedisTemplate.opsForValue().get(userRole);
                    List<String> selectRoles;
                    if (jsonRole != null) {
                        selectRoles = objectMapper.readValue(jsonRole, new TypeReference<>() {});
                    } else {
                        selectRoles = roleMapper.getSelectRoles(username);
                        String jsonMap = objectMapper.writeValueAsString(selectRoles);
                        stringRedisTemplate.opsForValue().set(
                                userRole, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                    }
                    if (selectRoles.isEmpty()) {
                        logger.error("{} - 權限不存在", username);
                        throw new ResourceNotFoundException(username + " - 權限不存在");
                    }
                    String allowedRoles = checkRole.value().getPermission();
                    boolean hasRole = selectRoles.stream().anyMatch(role -> role.equals(allowedRoles));
                    logger.info("指定權限： {}", allowedRoles);
                    logger.info("是否權限內： {}", (hasRole) ? "是" : "否");
                    String userCode = RedisKey.redisCommonAspectKey.get("userCode")
                            .replace("{1}", username)
                            .replace("{2}", allowedRoles);
                    String jsonCode = stringRedisTemplate.opsForValue().get(userCode);
                    Map<String, Object> code;
                    if (jsonCode != null) {
                        code = objectMapper.readValue(jsonCode, new TypeReference<>() {});
                    } else {
                        code = roleMapper.selectPermissionsCode(allowedRoles).get(allowedRoles);
                        String jsonMap = objectMapper.writeValueAsString(code);
                        stringRedisTemplate.opsForValue().set(
                                userCode, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                    }
                    String description = code.get("description").toString();
                    String permissions = userSelect.get("permissions").toString();
                    if (!hasRole) {
                        // 所需角色訪問被拒絕！
                        logger.error("角色權限({})訪問({})被拒絕，需要 : {}({})"
                                , permissions, allowedRoles, description, selectRoles);
                        throw new RuntimeException(
                                "角色權限(" + permissions + ")訪問(" + allowedRoles + ")被拒絕，" +
                                        "需要 : " + description + "(" + selectRoles + ")");
                    }
                    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                    String method = signature.getMethod().getName();
                    Map<String, Object> text = Map.of(
                            "method", method,
                            "roles", allowedRoles,
                            "permissions", permissions,
                            "description", description
                    );
                    Context.set(text);
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("checkRole 資源忙碌，請重試");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        try {
            return joinPoint.proceed();
        } finally {
            Context.clear(); // 很重要，避免 memory leak
        }
    }

}
