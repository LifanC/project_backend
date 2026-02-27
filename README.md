# API 功能 Demo 文件
## 1. 專案概述
- 這是一個基於 Spring Boot 的 REST API 伺服器
- 實作 Spring Security + JWT + Refresh Token 身份驗證
- 使用 Redis 作為快取
- 使用 MariaDB 作為主資料庫
- 專案為示範用的 API 系統，整合使用者身分驗證與權限控管機制。
系統採用 JWT 搭配 Refresh Token 的驗證架構，以確保 API 存取的安全性與可擴充性。
在系統架構上，使用 Redis 作為快取層，提升驗證與授權流程的效能；並以 MariaDB 作為主要資料庫，負責核心業務資料的持久化儲存。
## 2. 技術架構圖（Spring Boot → Redis → MariaDB）
架構說明：
1. Spring Boot 提供 API
- 接收前端或其他服務請求，負責業務邏輯處理
- 在操作共享資源（例如資料庫或快取）時，會使用分布式鎖控制，避免資料衝突
2. Redis 作為 Cache
- 快取熱點資料，減少對資料庫的直接查詢，提升效能
- 例如更新快取前先取得鎖，確保同一時間只有一個請求可以修改快取，避免資料不一致
3. MariaDB 作為主要資料存儲
- 在寫入資料庫前也可使用鎖，確保資料安全
- 例如：
- 1. 更新 Redis Cache
- 2. 更新 MariaDB
4. Redis + MariaDB 的協作：
- Redis
- 1. 資料存放在記憶體中，速度很快，但如果伺服器重啟，資料可能會丟失
- MariaDB
- 1. 資料持久化存儲
- 2. 每筆資料都會寫到硬碟，保證長期保存
- 3. 讀寫比較慢
## 3. 系統啟動方式（Run / docker-compose）
### 3.1 本地運行 (Run)
1. 編譯專案
- ./mvnw clean package
2. 啟動 Spring Boot
- java -jar target/demo-api.jar
### 3.2 Docker-Compose (Run)
1. 啟動 Docker
- 停止服務 + 清掉容器（加上 --rmi all 就連 image 也刪）
- docker compose down --rmi all
2. 重新啟動所有服務
- docker compose up *前景執行（會顯示 log）*
- docker compose up -d *背景執行*
3. 即時查看執行狀況
- docker compose logs *看 log（前景）*
- docker compose logs -f *持續追蹤 log*
4. 看目前有哪些服務在跑
- docker compose ps
5. 查看 mariadb
- docker exec -it <container_name> bash
- mariadb -u root -p
- show databases;
- USE 資料庫名稱;
- SHOW TABLES;
- SELECT * FROM permissionsdata;
6. 查看 redis
- docker exec -it <container_name> redis-cli
- KEYS *
- GET <key_name>
- TTL <key_name> *查看 TTL (剩餘時間)*
## 4. API 使用流程
![image](https://github.com/LifanC/project_backend/blob/master/permissions.png)
- 用 DTO（如 UserRequest），且搭配 Validation 註解。
- ## 權限設定
```
1.權限註冊
{
  "username": "zxc",
  "permissions": "USER",
  "message": "註冊權限帳號成功",
  "status": 201,
  "created_date": "2026-02-01T02:25:29",
  "updated_date": "2026-02-01T02:25:29",
  "users": []
}
2.權限查詢
{
  "username": "",
  "permissions": "",
  "message": "帳號查詢成功",
  "status": 200,
  "created_date": "2026-02-01T02:25:44.899185",
  "updated_date": "2026-02-01T02:25:44.899185",
  "users": [
    "zxc - USER",
    "asd - USER",
    "qwe - USER"
  ]
}
3.權限更改
{
  "username": "456",
  "permissions": "ADMIN",
  "message": "已更改權限",
  "status": 200,
  "created_date": "2026-02-01T02:26:39",
  "updated_date": "2026-02-01T02:26:55",
  "users": []
}
4.權限刪除
{
  "username": "789456123",
  "permissions": "USER",
  "message": "帳號已刪除",
  "status": 200,
  "created_date": "2026-02-01T02:27:39",
  "updated_date": "2026-02-01T02:27:39",
  "users": []
}
```
![image](https://github.com/LifanC/project_backend/blob/master/userUrl.png)
```
1.Token 取得
{
  "username": "zxc",
  "permissions": "USER",
  "message": "取得 Token 成功",
  "status": 200,
  "created_date": "2026-02-01T02:25:29",
  "updated_date": "2026-02-01T02:25:29"
}
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "連續錯誤5次，帳號暫時被鎖，請稍後再試(42秒)",
  "errors": {},
  "status": 500,
  "timestamp": "2026-02-10T10:14:30.6232878"
}
2.Token 驗證
{
  "username": "zxc",
  "permissions": "USER",
  "message": "有效",
  "status": 200,
  "created_date": "2026-02-01T02:25:29",
  "updated_date": "2026-02-01T02:25:29"
}
3.Token 登出
{
  "username": "zxc",
  "permissions": "USER",
  "message": "已登出",
  "status": 200,
  "created_date": "2026-02-01T02:25:29",
  "updated_date": "2026-02-01T02:25:29"
}
4.再一次 Token 驗證
{
  "code": "BAD_REQUEST",
  "message": "驗證使用者 Token 已過期，請重新取得 Token",
  "errors": {},
  "status": 400
}
5.查詢使用者名單(權限不夠)
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "Access denied! Required role: [ADMIN, MANAGER]",
  "errors": {},
  "status": 500
}
6.查詢使用者名單(查一般使用者) ADMIN、MANAGER 等級
{
  "username": "456",
  "permissions": "ADMIN",
  "message": "[zxc, asd, qwe]",
  "status": 200,
  "created_date": "2026-02-01T02:26:39",
  "updated_date": "2026-02-01T02:26:39"
}
7.新增訂單(有權限的限制) ADMIN、MANAGER 等級
{
  "username": "zxc",
  "permissions": "USER",
  "message": "新增訂單成功",
  "status": 200,
  "created_date": "2026-02-01T02:37:36",
  "updated_date": "2026-02-01T02:37:36",
  "order_item": "[QWE, ASD, ZXC]",
  "history": []
}
8.查詢訂單
{
  "username": "zxc",
  "permissions": "USER",
  "message": "查詢訂單成功",
  "status": 200,
  "created_date": "2026-02-01T02:37:36",
  "updated_date": "2026-02-01T02:37:36",
  "order_item": "[QWE, ASD, ZXC]",
  "history": []
}
9.更改訂單(有權限的限制) ADMIN、MANAGER 等級
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "Access denied! Required role: [ADMIN, MANAGER]",
  "errors": {},
  "status": 500
}
10.更改訂單(有權限的限制) ADMIN、MANAGER 等級
{
  "username": "zxc",
  "permissions": "USER",
  "message": "更改訂單成功",
  "status": 200,
  "created_date": "2026-02-01T02:37:36",
  "updated_date": "2026-02-01T02:40:45",
  "order_item": "[QWE, ASD, qqq]",
  "history": []
}
7.刪除訂單(有權限的限制) MANAGER 等級
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "Access denied! Required role: [MANAGER]",
  "errors": {},
  "status": 500
}
7.刪除訂單(有權限的限制) MANAGER 等級
{
  "username": "zxc",
  "permissions": "USER",
  "message": "訂單刪除成功",
  "status": 200,
  "created_date": "2026-02-01T02:37:36",
  "updated_date": "2026-02-01T02:40:45",
  "order_item": "[]",
  "history": []
}
8.歷史訂單(有權限的限制) MANAGER 等級
{
  "username": "123",
  "permissions": "USER",
  "message": "歷史紀錄查詢成功",
  "status": 200,
  "created_date": "2026-02-01T02:25:29",
  "updated_date": "2026-02-01T02:25:29",
  "order_item": "[]",
  "history": [
    "zxc - DELETE - []",
    "zxc - UPDATE - [QWE, ASD, qqq]",
    "zxc - INSERT - [QWE, ASD, ZXC]"
  ]
}
9.需要設定權限，就可以成功了
```
## 5. 目錄說明 / 層架構說明
```
project_backend/
├─ src/
│   ├─ main/
│   │   ├─ java/
│   │   │   └─ com.example.demo/
│   │   │       ├─ Aspect/              *放 AOP 切面*
│   │   │       ├─ Common/              *共用*
│   │   │       ├─ Config/              *模擬每次都要更新密鑰的方法*
│   │   │       ├─ Controller/          *API 控制器*
│   │   │       ├─ Dto/                 *請求、回應*
│   │   │       ├─ Exception/           *業務例外邏輯 自訂例外訊息*
│   │   │       ├─ Mapper/              *資料庫操作*
│   │   │       ├─ security/            *權限相關*
│   │   │       └─ Service/             *業務邏輯*
│   │   └─ resources/
│   │       ├─ application.yml          *配置檔*
│   │       ├─ application-docker.yml   *Docker 配置檔*
│   │       ├─ log4j2.xml               *顯示 log*
│   │       └─ certs/                   *放憑證的檔案
│   └─ test/                            *單元測試*
├─ docker-compose.yml
└─ Dockerfile

```
## 6. 用到的設計模式與思維
### 1. 提供使用者身分驗證與權限控管機制，確保僅授權使用者可存取對應的 API 資源。
- *GUEST（客人）可執行功能：查詢、新增、刪除訂單。*
- *USER（一般使用者）可執行功能：查詢、新增、修改、刪除訂單。*
- *ADMIN（系統管理員）可執行功能：查詢、修改、刪除、檢視歷史紀錄。* <*可查詢之對象資料：GUEST、USER。*>
- *MANAGER（部門主管）可執行功能：查詢、刪除、檢視歷史紀錄。* <*可查詢之對象資料：GUEST、USER。*>
### 2. JWT 驗證流程: Token 驗證 → Redis 檢查 → 允許存取
- *商業邏輯層 : Controller 只負責接收與回傳，Service 處理業務邏輯*
