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
                                                        is_active bool NOT NULL DEFAULT false
);

-- 建立觸發器
CREATE TRIGGER trigger_update_updated_date
    BEFORE UPDATE ON interviewworks_schema.userdata_details
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
ALTER SEQUENCE interviewworks_schema.roles_id_seq RESTART WITH 1;
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
ALTER SEQUENCE interviewworks_schema.permissions_id_seq RESTART WITH 1;
INSERT INTO interviewworks_schema.permissions (code, description)
VALUES ('user:item:query', 'ADMIN'),
       ('car:item:create', 'GUEST'),
       ('car:item:query', 'GUEST'),
       ('car:item:update', 'GUEST'),
       ('car:item:delete', 'GUEST'),
       ('car:item:history', 'GUEST'),
       ('user:item:token', 'GUEST'),
       ('orderbackend:item:token', 'ADMIN'),
       ('user:item:confirm', 'GUEST'),
       ('user:item:quotations', 'GUEST');

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
       (1, 4),
       (1, 5),
       (1, 6),
       (3, 1),
       (1, 7),
       (3, 8),
       (1, 9),
       (1, 10);

-- interviewworks_schema.products definition

-- Drop table

-- DROP TABLE interviewworks_schema.products;

CREATE TABLE interviewworks_schema.products (
                                                product_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                                products_name varchar NULL,
                                                price int8 NULL DEFAULT 0,
                                                stock int8 NULL DEFAULT 0,
                                                description varchar NULL,
                                                created_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 建立觸發器
CREATE TRIGGER trigger_update_updated_date
    BEFORE UPDATE ON interviewworks_schema.products
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_date();

-- interviewworks_schema.quotations definition

-- Drop table

-- DROP TABLE interviewworks_schema.quotations;

CREATE TABLE interviewworks_schema.quotations (
                                                  quotation_id int8 NULL,
                                                  username varchar(10) NOT NULL,
                                                  status varchar(100) NOT NULL DEFAULT NULL::character varying,
                                                  total_price int8 DEFAULT 0,
                                                  created_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  updated_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  CONSTRAINT quotations_pk PRIMARY KEY (quotation_id),
                                                  CONSTRAINT quotations_status_check CHECK (
                                                    status IN ('estimate', 'sent', 'accepted', 'rejected')
                                                  )
);

-- 建立觸發器
CREATE TRIGGER trigger_update_updated_date
    BEFORE UPDATE ON interviewworks_schema.quotations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_date();

-- interviewworks_schema.quotation_items definition

-- Drop table

-- DROP TABLE interviewworks_schema.quotation_items;

CREATE TABLE interviewworks_schema.quotation_items (
                                                       quotation_id int4 NULL,
                                                       product_id int4 NULL,
                                                       quantity int8 NULL DEFAULT 0,
                                                       price int8 NULL DEFAULT 0,
                                                       created_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                       updated_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                       unit_percent int8 NULL DEFAULT 0,
                                                       unit_price int8 NULL DEFAULT 0,
                                                       CONSTRAINT quotation_items_fk FOREIGN KEY (product_id) REFERENCES interviewworks_schema.products(product_id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- 建立觸發器
CREATE TRIGGER trigger_update_updated_date
    BEFORE UPDATE ON interviewworks_schema.quotation_items
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_date();

-- interviewworks_schema.orders definition

-- Drop table

-- DROP TABLE interviewworks_schema.orders;

CREATE TABLE interviewworks_schema.orders (
                                              order_id INT NULL,
                                              quotation_id INT NULL,
                                              status varchar(100) NOT NULL DEFAULT NULL::character varying,
                                              total_price int8 NULL DEFAULT 0,
                                              created_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              updated_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              CONSTRAINT orders_pk PRIMARY KEY (quotation_id),
                                              CONSTRAINT orders_status_check CHECK (
                                                status IN ('pending', 'confirmed', 'cancelled')
                                              )
);

-- 建立觸發器
CREATE TRIGGER trigger_update_updated_date
    BEFORE UPDATE ON interviewworks_schema.orders
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_date();

-- interviewworks_schema.shipments definition

-- Drop table

-- DROP TABLE interviewworks_schema.shipments;

CREATE TABLE interviewworks_schema.shipments (
                                                 order_id INT NULL,
                                                 status varchar(100) NOT NULL DEFAULT NULL::character varying,
                                                 prefix varchar NULL,
                                                 date_part varchar NULL,
                                                 serial varchar NULL,
                                                 tracking_number varchar NULL,
                                                 created_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 updated_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 CONSTRAINT shipments_pk PRIMARY KEY (order_id),
                                                 CONSTRAINT shipments_status_check CHECK (
                                                     status IN ('preparing', 'shipped', 'delivered')
                                                     ),
                                                 CONSTRAINT shipments_fk FOREIGN KEY (order_id) REFERENCES interviewworks_schema.orders(order_id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- 建立觸發器
CREATE TRIGGER trigger_update_updated_date
    BEFORE UPDATE ON interviewworks_schema.shipments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_date();

-- interviewworks_schema.payments definition

-- Drop table

-- DROP TABLE interviewworks_schema.payments;

CREATE TABLE interviewworks_schema.payments (
                                                order_id INT NULL,
                                                amount int8 NULL DEFAULT 0,
                                                status varchar(100) NOT NULL DEFAULT NULL::character varying,
                                                payments_method varchar(100) NOT NULL DEFAULT NULL::character varying,
                                                created_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                CONSTRAINT payments_status_check CHECK (
                                                    status IN ('unpaid', 'partial', 'paid')
                                                ),
                                                CONSTRAINT payments_pk PRIMARY KEY (order_id),
                                                CONSTRAINT payments_payments_method_check CHECK (
                                                    status IN ('cash', 'credit_card', 'transfer')
                                                ),
                                                CONSTRAINT payments_fk FOREIGN KEY (order_id) REFERENCES interviewworks_schema.orders(order_id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- 建立觸發器
CREATE TRIGGER trigger_update_updated_date
    BEFORE UPDATE ON interviewworks_schema.payments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_date();

-- 確保 sequence 從 1 開始（清空表格並重置）
ALTER SEQUENCE interviewworks_schema.products_product_id_seq RESTART WITH 1;

-- 插入假資料範例（50筆）
INSERT INTO interviewworks_schema.products (products_name, price, stock, description)
SELECT
    CONCAT('測試', n) AS products_name,
    FLOOR(100 + RANDOM() * 900) AS price,   -- 價格 100~1000
    FLOOR(RANDOM() * 100) AS stock,          -- 庫存 0~99
    CONCAT('描述', n) AS description
FROM generate_series(1,50) AS s(n);



