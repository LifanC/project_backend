package com.example.demo.Aspect;

import com.example.demo.Common.Context;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Aspect
@Component
public class CommonAspect {

    private final Logger logger = LoggerFactory.getLogger(CommonAspect.class);

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public CommonAspect(
            UserMapper userMapper,
            RoleMapper roleMapper,
            StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.stringRedisTemplate = stringRedisTemplate;
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
                    UserData userData = new UserData(req.getUsername());
                    Map<String, Object> userSelect = getUserData(userData);
                    if (userSelect == null) {
                        logger.error("帳號不存在");
                        throw new ResourceNotFoundException("帳號不存在");
                    }

                    List<String> selectRoles = roleMapper.getSelectRoles(req.getUsername());
                    String allowedRoles = checkRole.value().getPermission();

                    // 只要使用者有其中一個角色就通過
                    boolean hasRole = selectRoles.stream().anyMatch(role -> role.equals(allowedRoles));
                    logger.info("指定權限： {}", allowedRoles);
                    logger.info("是否權限內： {}", (hasRole) ? "是" : "否");
                    Map<String, Object> code = roleMapper.selectPermissionsCode(allowedRoles).get(allowedRoles);
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
