-- 建資料庫（如果 docker-compose 沒設定 MariaDB_DATABASE，也可以寫）
CREATE DATABASE IF NOT EXISTS interviewworks;

USE interviewworks;

-- interviewworks.permissionsdata definition

CREATE TABLE IF NOT EXISTS `permissionsdata` (
                          `username` varchar(10) NOT NULL COMMENT '名稱',
                          `password` varchar(100) NOT NULL COMMENT '密碼',
                          `use_permissions` enum('GUEST','USER','ADMIN','MANAGER') NOT NULL COMMENT '權限',
                          `created_date` timestamp NOT NULL DEFAULT current_timestamp(),
                          `updated_date` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
                          PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_vietnamese_ci;

-- interviewworks.secret definition

CREATE TABLE IF NOT EXISTS `secret` (
                          `secret_number` varchar(100) NOT NULL,
                          `created_date` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_vietnamese_ci;

-- interviewworks.userdata definition

CREATE TABLE IF NOT EXISTS `userdata` (
                            `username` varchar(10) NOT NULL COMMENT '名稱',
                            `created_date` timestamp NOT NULL DEFAULT current_timestamp(),
                            `updated_date` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
                            PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_vietnamese_ci;

-- interviewworks.userdata_details definition

CREATE TABLE IF NOT EXISTS `userdata_details` (
                            `username` varchar(10) NOT NULL COMMENT '名稱',
                            `created_date` timestamp NOT NULL DEFAULT current_timestamp(),
                            `updated_date` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
                            `order_item` varchar(100) DEFAULT NULL COMMENT '訂單',
                            PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_vietnamese_ci;

-- interviewworks.userdata_details_u definition

CREATE TABLE IF NOT EXISTS `userdata_details_u` (
                            `username` varchar(10) NOT NULL COMMENT '名稱',
                            `created_date` timestamp NOT NULL DEFAULT current_timestamp(),
                            `updated_date` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
                            `order_item` varchar(100) DEFAULT NULL COMMENT '訂單',
                            `action_type` enum('insert','update','delete') NOT NULL,
                            KEY `userdata_details_u_FK` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_vietnamese_ci;

-- interviewworks.roles definition

CREATE TABLE IF NOT EXISTS `roles` (
                            `id` bigint(20) NOT NULL AUTO_INCREMENT,
                            `name` varchar(100) DEFAULT NULL COMMENT '權限名稱',
                            `description` varchar(100) DEFAULT NULL COMMENT '敘述',
                            PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='roles（角色表）';

-- 初始化資料

INSERT INTO interviewworks.roles (name, description)
VALUES ('GUEST', '客人'),
       ('USER', '一般使用者'),
       ('ADMIN', '系統管理員'),
       ('MANAGER', '部門主管');

-- interviewworks.permissions definition

CREATE TABLE IF NOT EXISTS `permissions` (
                            `id` bigint(20) NOT NULL AUTO_INCREMENT,
                            `code` varchar(100) DEFAULT NULL,
                            `description` varchar(100) DEFAULT NULL COMMENT '敘述',
                            PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='permissions（權限表）';

-- 初始化資料

INSERT INTO interviewworks.permissions (code, description)
VALUES ('user:item:query', 'ADMIN、MANAGER'),
       ('order:item:query', 'GUEST、USER、ADMIN、MANAGER'),
       ('order:item:create', 'GUEST、USER'),
       ('order:item:update', 'USER、ADMIN'),
       ('order:item:delete', 'GUEST、USER、ADMIN、MANAGER'),
       ('order:item:history', 'ADMIN、MANAGER');

-- interviewworks.user_role definition

CREATE TABLE IF NOT EXISTS `user_role` (
                             `username` varchar(10) NOT NULL COMMENT '名稱',
                             `role_id` bigint(20) NOT NULL,
                             PRIMARY KEY (`username`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='user_role（使用者-角色 關聯表）';

-- interviewworks.role_permission definition

CREATE TABLE IF NOT EXISTS `role_permission` (
                             `role_id` bigint(20) NOT NULL,
                             `permission_id` bigint(20) NOT NULL,
                             PRIMARY KEY (`role_id`,`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='role_permission（角色-權限 關聯表）';

-- 初始化資料

INSERT INTO interviewworks.role_permission (role_id, permission_id)
VALUES (1, 2),
       (1, 3),
       (1, 5),
       (2, 2),
       (2, 3),
       (2, 4),
       (2, 5),
       (3, 1),
       (3, 2),
       (3, 4),
       (3, 5),
       (3, 6),
       (4, 1),
       (4, 2),
       (4, 5),
       (4, 6);

