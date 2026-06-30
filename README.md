# API 功能 Demo 文件

## 1. 專案概述
- 這是一個基於 Spring Boot 的 REST API
- 實作 JWT + Refresh Token 身份驗證 + 黑名單
- 使用 Redis 作為快取
- 使用 PostgreSQL 作為主資料庫
- 專案為示範用的 API 系統，整合使用者身分驗證與權限控管機制。
- 在系統架構上，使用 Redis 作為快取層，提升驗證與授權流程的效能；並以 PostgreSQL 作為主要資料庫，負責核心業務資料的持久化儲存。
- 系統已透過 Docker Compose 容器化部署，並可部署至 Render。

## 2. 系統功能
1. 使用者註冊
2. 使用者登入
3. JWT Access Token 驗證
4. Refresh Token 換發機制
5. 使用者登出
6. Token 黑名單管理
7. 角色權限控管（RBAC）
8. 使用者資料查詢與修改

## 3. 技術棧
- Java 21
- Spring Boot 3.x
- Mybatis
- JWT
- Redis
- PostgreSQL
- Docker

## 4. 身份驗證流程
1. 使用者登入並提交帳號密碼
2. 後端驗證帳號密碼
3. 成功後發放 Access Token 與 Refresh Token（JWT）
4. 使用 Access Token 存取受保護 API
5. Access Token 過期時，使用 Refresh Token 換發新 Token
6. 使用者登出時，將 Token 加入 Redis 黑名單，防止再次使用

## 5. API
用swagger-ui

本專案使用 Swagger UI 提供 API 文件。

啟動專案後，可於以下網址查看：

- Swagger UI：網址/api/swagger-ui/index.html
- OpenAPI JSON：網址/api/v3/api-docs

操作方式：

1. 啟動後端服務。
2. 開啟 `網址/api/swagger-ui/index.html`。
3. 選擇欲測試的 API。
4. 點選 **Try it out**。
5. 輸入參數並執行請求。

提供功能包含：

- 使用者管理
- 商品管理
- 訂單管理
- JWT 驗證
- RESTful API 測試

Swagger 提供：

- API 路徑與 HTTP Method
- Request Parameters
- Request Body
- Response 格式
- Status Code
- Try it out 測試功能

## 6. 權限模型
### GUEST
- 查詢個人資料
- 修改個人資料
### ADMIN
- 管理所有使用者
- 查看系統資訊

## 7. Redis 使用場景
- JWT 黑名單管理
- Refresh Token 快取

## 8. 技術架構圖（Spring Boot → Redis → PostgreSQL）
Client → Spring Boot API → Service Layer → Redis / PostgreSQL

- Controller：只處理 Request / Response
- Service：業務邏輯
- Redis：Cache / Token
- PostgreSQL：持久化資料

架構說明：
1. Spring Boot 提供 API
- 接收前端或其他服務請求，負責業務邏輯處理
- 在操作共享資源（例如資料庫或快取）時，會使用分布式鎖控制，避免資料衝突
2. Redis 作為 Cache
- 快取熱點資料，減少對資料庫的直接查詢，提升效能
- 例如更新快取前先取得鎖，確保同一時間只有一個請求可以修改快取，避免資料不一致
3. PostgreSQL 作為主要資料存儲
- 在寫入資料庫前也可使用鎖，確保資料安全
- 例如：
- 1. 更新 Redis Cache
- 2. 更新 PostgreSQL
4. Redis + PostgreSQL 的協作：
- Redis
- 1. 資料存放在記憶體中，速度很快，但如果伺服器重啟，資料可能會丟失
- PostgreSQL
- 1. 資料持久化存儲
- 2. 每筆資料都會寫到DB，保證長期保存
- 3. 讀寫比較慢
## 9. 系統啟動方式（Run / docker-compose）
### 9.1 本地運行 (Run)
1. 啟動 Spring Boot
### 9.2 Docker-Compose (Run)
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
5. 查看 PostgreSQL
- docker exec -it <container_name> psql -U postgres
- \c interviewworks
- \dn
- \dt interviewworks_schema.*
- SELECT * FROM interviewworks_schema.roles;
- 或
- SET search_path TO interviewworks_schema;
- SELECT * FROM roles;
6. 查看 redis
- docker exec -it <container_name> redis-cli
- KEYS *
- GET <key_name>
- TTL <key_name> *查看 TTL (剩餘時間)*

## 10. JWT 驗證流程: Token 驗證 → Redis 檢查 → 允許存取
- *商業邏輯層 : Controller 只負責接收與回傳，Service 處理業務邏輯*
1. Controller
- 接收 Request、回傳 Response、驗證 Request Body、不負責商業邏輯
2. Service
- 商業邏輯、Token 驗證、RBAC 權限判斷、呼叫 Repository
3. Repository（MyBatis）
- SQL 操作、CRUD、Transaction
4. Redis
- Refresh Token、JWT Blacklist、Cache、TTL 管理
5. PostgreSQL
- 使用者資料、商品、訂單、報價、出貨、收款

## 11. 額外功能
Customer → Quotation → Order → Shipment → Payment
### 1. 📄 報價管理
- *建立報價*
- *編輯商品*
- *送出報價*
### 2. 📦 訂單管理
- *從報價轉訂單*
- *查看訂單詳情*
### 3. 🚚 出貨管理
- *建立出貨單*
- *更新物流狀態*
### 4. 💰 收款管理
- *新增付款*
- *查看未付款訂單*
## 8. 設計流程
- *1. 客戶（Customer）*
- *2. 商品（Product）*
- *3. 報價（Quotation）*
- *4. 訂單（Order）*
- *5. 出貨（Shipment）*
- *6. 付款（Payment）*
- *流程：Customer → Quotation → Order → Shipment → Payment*
- *~*
- *報價（quotations）*
- *status -- estimate（預估） / sent（已送出） / accepted（接受） / rejected（拒絕）*
- *~*
- *訂單（orders）*
- *quotation_id -- 從報價轉來*
- *status -- pending（待處理） / confirmed（已確認） / cancelled（取消）*
- *~*
- *出貨（shipments）*
- *order_id -- 從訂單轉來*
- *status -- preparing（備貨中） / shipped（已出貨） / delivered（已送達）*
- *~*
- *付款（payments）*
- *order_id -- 從訂單轉來*
- *status -- unpaid（未付） / partial（部分） / paid（已付）*
- *method -- cash（現金） / credit_card（信用卡） / transfer（轉帳）*
- *~*
- *1. 一個 customer → 多個 quotations*
- *2. 一個 quotation → 多個 quotation_items*
- *3. 一個 quotation（accepted）→ 一個 order*
- *4. 一個 order → 多個 shipments*
- *5. 一個 order → 多個 payments*
## 12. 專案特色
- RESTful API 設計
- JWT Access Token 驗證
- Refresh Token 自動換發
- Redis Token Blacklist
- RBAC 權限控管
- MyBatis 資料存取
- Docker Compose 容器化部署
- Render 雲端部署
- Swagger API 文件
- PostgreSQL 持久化儲存
- Redis 快取機制
- 分層式架構（Controller / Service / Repository）
1. 分散式鎖（Lock）
2. 為避免多個請求同時操作共享資源造成資料不一致，系統在關鍵業務流程中使用 Java Lock 控制併發。
3. 使用場景：
- 1. 建立訂單
- 2. 更新庫存
- 3. 更新快取
- 4. Refresh Token 換發
- 5. 避免重複提交（Duplicate Request）
4. Java Lock
- 1. ReentrantLock
5. 防止 Cache Avalanche（快取雪崩）
- 1. TTL 加入隨機值（Random Expiration），避免大量 Key 在同一時間過期。
6. 登入安全機制（Login Security）
- 1. 為防止暴力破解（Brute Force Attack）與惡意登入嘗試，系統實作登入失敗次數限制。
- 2. 功能
- - 記錄使用者登入失敗次數
- - 超過限制後暫時鎖定帳號
- - 使用 Redis 記錄失敗次數與鎖定時間（TTL）
- - 鎖定期間拒絕登入請求
- - TTL 到期後自動解除鎖定
7. Redis Key 命名規範
- 1. 系統採用 統一 Redis Key 命名規範，確保快取資料具備一致性、可讀性與可維護性。
- - 命名格式：[業務模組]:[資料分類]:[識別ID]:[屬性]
- 'user:{module}:{type}:{identifier}'
- 2. 設計說明
- - module：功能模組（jwt / auth / cache...）
- - type：資料類型（refresh / access / lock / fail / blacklist...）
- - identifier：唯一識別（userId / tokenId...