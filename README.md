# API 功能 Demo 文件

## 1. 專案簡介
本專案是一個示範用的 API 系統，提供使用者登入、登出功能，採用 JWT + Refresh Token 機制保護 API，並使用 Redis 作為快取，MariaDB 作為主要資料庫。

## 2. 技術架構圖（Spring Boot → Redis → MariaDB）
> 架構說明：
>> 1. Spring Boot 提供 API
>> 2. Redis 作為 Cache
>> 3. MariaDB 作為主要資料存儲

## 3. 系統啟動方式（Run / docker-compose）
> ### 3.1 本地運行 (Run)
>> #### 編譯專案
>> ./mvnw clean package
>> #### 啟動 Spring Boot
>> java -jar target/demo-api.jar
> ### 3.2 Docker-Compose (Run)
>> #### 停止服務 + 清掉容器（加上 --rmi all 就連 image 也刪）
>> docker compose down --rmi all
>> #### 重新啟動所有服務
>> docker compose up -d
>> #### 即時查看執行狀況
>> docker compose logs -f

## 4. API 清單 + Demo 範例
> ### 4.1 註冊
```
URL: /api/auth/register
Method: POST
Request:
{
  "username": "XXX",
  "password": "XXX"
}
Response:
{
  "path": "/api/auth/register",
  "detail": "內容",
  "status": 200,
  "timestamp": "2026-01-10T10:59:40.964862126"
}
```

> ### 4.2 登入
```
URL: /api/auth/login?username=XXX&password=XXX
Method: GET
Response:
{
  "path": "/api/auth/login",
  "detail": "內容",
  "status": 200,
  "timestamp": "2026-01-10T11:05:24.855616544"
}
```

> ### 4.3 更改密碼
```
URL: /api/auth/updatePassword
Method: PUT
Request:
{
  "username": "XXX",
  "password": "XXX"
}
Response:
{
  "path": "/api/auth/updatePassword",
  "detail": "內容",
  "status": 200,
  "timestamp": "2026-01-10T10:59:40.964862126"
}
```

> ### 4.4 驗證 Token
```
URL: /api/auth/validate?token=XXXXXXXXXX
Method: GET
Response:
{
  "path": "/api/auth/validate",
  "detail": "內容",
  "status": 200,
  "timestamp": "2026-01-10T11:05:24.855616544"
}
```

> ### 4.5 查詢 Token
```
URL: /api/auth/query?username=XXX&password=XXX
Method: GET
Response:
{
  "path": "/api/auth/query",
  "detail": "內容",
  "status": 200,
  "timestamp": "2026-01-10T11:05:24.855616544"
}
```

> ### 4.6 登出 Token
```
URL: /api/auth/logout
Method: DELETE
Request:
{
  "token": "XXXXXXXXXX"
}
Response:
{
  "path": "/api/auth/logout",
  "detail": "內容",
  "status": 200,
  "timestamp": "2026-01-10T11:16:24.848199895"
}
```

> 1. API->>DB: 驗證帳號密碼
> 2. DB ->>API: 回傳驗證結果
> 3. API->>Redis: 儲存 Token
> 4. API->>Client: 回傳 accessToken & refreshToken

## 5. 目錄說明 / 層架構說明
```
src/
 ├─ main/
 │   ├─ java/
 │   │   └─ com.example.demo/
 │   │       ├─ Config/       * 模擬每次都要更新密鑰的方法 *
 │   │       ├─ Controller/   * API 控制器 *
 │   │       ├─ Exception/    * 業務例外邏輯 自訂例外訊息 *
 │   │       ├─ Mapper/       * 資料庫操作 *
 │   │       └─ Service/      * 業務邏輯 *
 │   └─ resources/
 │       ├─ application.yml   * 配置檔 *
 │       ├─ application-docker.yml   * Docker 配置檔 *
 │       ├─ log4j2.xml        * 顯示 log *
 │       └─ static/
 └─ test/                     * 單元測試 *
```

## 6. 用到的設計模式與思維
> 1. Singleton 單列模式 : Redis 連線管理
> 2. Factory 工廠模式 : 建立不同類型的 API Response
> 3. JWT 驗證流程: Token 驗證 → Redis 檢查 → 允許存取
> 4. Service Layer 商業邏輯層 : Controller 只負責接收與回傳，Service 處理業務邏輯