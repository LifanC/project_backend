CREATE SCHEMA IF NOT EXISTS interviewworks_schema;

-- 建立觸發器函數
CREATE OR REPLACE FUNCTION update_updated_date()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_date = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- interviewworks.permissionsdata definition

-- Drop table

-- DROP TABLE interviewworks_schema.permissionsdata;

CREATE TABLE IF NOT EXISTS interviewworks_schema.permissionsdata (
                                                       username varchar(10) NOT NULL,
                                                       "password" varchar(100) NOT NULL,
                                                       use_permissions varchar(20) NULL,
                                                       created_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                       updated_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                       CONSTRAINT permissionsdata_pkey PRIMARY KEY (username),
                                                       CONSTRAINT permissionsdata_use_permissions_check CHECK (((use_permissions)::text = ANY ((ARRAY['GUEST'::character varying, 'USER'::character varying, 'ADMIN'::character varying, 'MANAGER'::character varying])::text[])))
);

-- 建立觸發器
CREATE TRIGGER trigger_update_updated_date
    BEFORE UPDATE ON interviewworks_schema.permissionsdata
    FOR EACH ROW
EXECUTE FUNCTION update_updated_date();

-- interviewworks.secret definition

-- Drop table

-- DROP TABLE interviewworks_schema.secret;

CREATE TABLE IF NOT EXISTS interviewworks_schema.secret (
                                              secret_number varchar(100) NOT NULL,
                                              created_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- interviewworks.userdata definition

-- Drop table

-- DROP TABLE interviewworks_schema.userdata;

CREATE TABLE IF NOT EXISTS interviewworks_schema.userdata (
                                                username varchar(10) NOT NULL,
                                                created_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                CONSTRAINT userdata_pkey PRIMARY KEY (username)
);

-- 建立觸發器
CREATE TRIGGER trigger_update_updated_date
    BEFORE UPDATE ON interviewworks_schema.userdata
    FOR EACH ROW
EXECUTE FUNCTION update_updated_date();

-- interviewworks.userdata_details definition

-- Drop table

-- DROP TABLE interviewworks_schema.userdata_details;

CREATE TABLE IF NOT EXISTS interviewworks_schema.userdata_details (
                                                        username varchar(10) NOT NULL,
                                                        created_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                        updated_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                        order_item varchar(100) NULL DEFAULT NULL::character varying,
                                                        CONSTRAINT userdata_details_pkey PRIMARY KEY (username)
);

-- 建立觸發器
CREATE TRIGGER trigger_update_updated_date
    BEFORE UPDATE ON interviewworks_schema.userdata_details
    FOR EACH ROW
EXECUTE FUNCTION update_updated_date();

-- interviewworks.userdata_details_u definition

-- Drop table

-- DROP TABLE interviewworks_schema.userdata_details_u;

CREATE TABLE IF NOT EXISTS interviewworks_schema.userdata_details_u (
                                                          username varchar(10) NOT NULL,
                                                          created_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                          updated_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                          order_item varchar(100) NULL DEFAULT NULL::character varying,
                                                          action_type varchar(100) NULL DEFAULT NULL::character varying,
                                                          CONSTRAINT userdata_details_u_action_type_check CHECK (((action_type)::text = ANY ((ARRAY['insert'::character varying, 'update'::character varying, 'delete'::character varying])::text[]))),
                                                          CONSTRAINT userdata_details_u_pkey PRIMARY KEY (username)
);

-- 建立觸發器
CREATE TRIGGER trigger_update_updated_date
    BEFORE UPDATE ON interviewworks_schema.userdata_details_u
    FOR EACH ROW
EXECUTE FUNCTION update_updated_date();

-- interviewworks.roles definition

-- Drop table

-- DROP TABLE interviewworks_schema.roles;

CREATE TABLE IF NOT EXISTS interviewworks_schema.roles (
                                             id int8 NOT NULL GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE),
                                             "name" varchar(100) NULL,
                                             description varchar(100) NULL,
                                             CONSTRAINT roles_pkey PRIMARY KEY (id)
);

-- 初始化資料

INSERT INTO interviewworks_schema.roles (name, description)
VALUES ('GUEST', '客人'),
       ('USER', '一般使用者'),
       ('ADMIN', '系統管理員'),
       ('MANAGER', '部門主管');

-- interviewworks.permissions definition

-- Drop table

-- DROP TABLE interviewworks_schema.permissions;

CREATE TABLE IF NOT EXISTS interviewworks_schema.permissions (
                                                   id int8 NOT NULL GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE),
                                                   code varchar(100) NULL DEFAULT NULL::character varying,
                                                   description varchar(100) NULL DEFAULT NULL::character varying,
                                                   CONSTRAINT permissions_pkey PRIMARY KEY (id)
);

-- 初始化資料

INSERT INTO interviewworks_schema.permissions (code, description)
VALUES ('user:item:query', 'ADMIN、MANAGER'),
       ('order:item:query', 'GUEST、USER、ADMIN、MANAGER'),
       ('order:item:create', 'GUEST、USER'),
       ('order:item:update', 'USER、ADMIN'),
       ('order:item:delete', 'GUEST、USER、ADMIN、MANAGER'),
       ('order:item:history', 'ADMIN、MANAGER');

-- interviewworks.user_role definition

-- Drop table

-- DROP TABLE interviewworks_schema.user_role;

CREATE TABLE IF NOT EXISTS interviewworks_schema.user_role (
                                                 username varchar(10) NOT NULL,
                                                 role_id int8 NOT NULL,
                                                 CONSTRAINT user_role_pk PRIMARY KEY (username, role_id)
);

-- interviewworks.role_permission definition

-- Drop table

-- DROP TABLE interviewworks_schema.role_permission;

CREATE TABLE IF NOT EXISTS interviewworks_schema.role_permission (
                                                       role_id int8 NOT NULL,
                                                       permission_id int8 NOT NULL,
                                                       CONSTRAINT role_permission_pk PRIMARY KEY (role_id, permission_id)
);

-- 初始化資料

INSERT INTO interviewworks_schema.role_permission (role_id, permission_id)
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


