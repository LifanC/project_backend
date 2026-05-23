# API 功能 Demo 文件

## 1. 專案概述
- 這是一個基於 Spring Boot 的 REST API
- 實作 Spring Security + JWT + Refresh Token 身份驗證 + 黑名單
- 使用 Redis 作為快取
- 使用 PostgreSQL 作為主資料庫
- 專案為示範用的 API 系統，整合使用者身分驗證與權限控管機制。
在系統架構上，使用 Redis 作為快取層，提升驗證與授權流程的效能；並以 PostgreSQL 作為主要資料庫，負責核心業務資料的持久化儲存。

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
- JDBC
- JWT
- Redis
- PostgreSQL
- Docker

## 4. 身份驗證流程
1. 使用者登入
2. 驗證帳號密碼
3. 發放 Access Token 與 Refresh Token
4. Access Token 驗證 API 存取
5. Access Token 過期後使用 Refresh Token 換發新 Token
6. 登出時將 Token 加入 Redis 黑名單

## 5. API清單
  <table border="1">
  Permissions
      <tr>
          <th>Method</th>
          <th>Path</th>
          <th>說明</th>
      </tr>
      <tr>
          <td>GET</td>
          <td>/v1/permissions/testLogin</td>
          <td>testLogin</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/permissions/register</td>
          <td>register</td>
      </tr>
      <tr>
          <td>GET</td>
          <td>/v1/permissions/query</td>
          <td>query</td>
      </tr>
      <tr>
          <td>PUT</td>
          <td>/v1/permissions/update</td>
          <td>update</td>
      </tr>
      <tr>
          <td>DELETE</td>
          <td>/v1/permissions/delete</td>
          <td>delete</td>
      </tr>
  </table>
  <table border="1">
  User
      <tr>
          <th>Method</th>
          <th>Path</th>
          <th>說明</th>
      </tr>
      <tr>
          <td>GET</td>
          <td>/v1/user/testLogin</td>
          <td>testLogin</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/takeToken</td>
          <td>takeToken</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/validate</td>
          <td>validate</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/logout</td>
          <td>logout</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/productsCarSelect</td>
          <td>查詢商品</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/createCarItem</td>
          <td>新增購物車</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/queryCarItem</td>
          <td>查詢購物車</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/updateCarItem</td>
          <td>更改購物車</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/deleteCarItem</td>
          <td>刪除購物車</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/confirmItem</td>
          <td>確認訂單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/quotationsProductId</td>
          <td>查詢報價單編號</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/quotationsProduct</td>
          <td>查詢報價單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/userAccepted</td>
          <td>接受報價單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/userRejected</td>
          <td>拒絕報價單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/userShipments</td>
          <td>查詢出貨資訊</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/userPayments</td>
          <td>查詢付款資訊</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/user/userPayMoney</td>
          <td>付款</td>
      </tr>
  </table>
  <table border="1">
  Orderbackend
      <tr>
          <th>Method</th>
          <th>Path</th>
          <th>說明</th>
      </tr>
      <tr>
          <td>GET</td>
          <td>/v1/orderbackend/testLogin</td>
          <td>testLogin</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/takeToken</td>
          <td>takeToken</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/validate</td>
          <td>validate</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/logout</td>
          <td>logout</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/queryUser</td>
          <td>查用戶</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/quotationsProductItem</td>
          <td>查詢用戶商品報價</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/confirmQuotationsProductItem</td>
          <td>確認報價單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/deleteQuotationsProduct</td>
          <td>刪除報價單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/queryQuotationsProduct</td>
          <td>查詢報價單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/sendQuotationsProduct</td>
          <td>送出報價單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/ordersUser</td>
          <td>查詢用戶訂單名單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/ordersProduct</td>
          <td>查詢訂單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/ordersConfirmed</td>
          <td>確認訂單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/ordersCancelled</td>
          <td>取消訂單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/shipmentsTrackingNumber</td>
          <td>查詢用戶出貨名單</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/shipmentsShipped</td>
          <td>已出貨</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/shipmentsDelivered</td>
          <td>已送達</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/orderbackend/shipmentsRollback</td>
          <td>恢復出貨狀態</td>
      </tr>
  </table>
  <table border="1">
  Products
      <tr>
          <th>Method</th>
          <th>Path</th>
          <th>說明</th>
      </tr>
      <tr>
          <td>GET</td>
          <td>/v1/products/testLogin</td>
          <td>testLogin</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/products/insert</td>
          <td>insert</td>
      </tr>
      <tr>
          <td>POST</td>
          <td>/v1/products/select</td>
          <td>select</td>
      </tr>
      <tr>
          <td>PUT</td>
          <td>/v1/products/update</td>
          <td>update</td>
      </tr>
      <tr>
          <td>DELETE</td>
          <td>/v1/products/delete</td>
          <td>delete</td>
      </tr>
  </table>

Request：取token

    {
        "username": "luke1",
        "password": "qwe123"
    }

Response：

    {
      "code": "200 OK",
      "status": 200,
      "data": [
        {
          "created_date": "2026-05-23 20:24:42",
          "permissions": "GUEST",
          "remark": "Token 取得成功",
          "username": "luke1"
        }
      ],
      "timestamp": "2026-05-23 20:24:42"
    }

Request：驗證token

    {
        "username": "luke1"
    }

Response：

    {
      "code": "200 OK",
      "status": 200,
      "data": [
        {
          "created_date": "2026-05-23 20:37:45",
          "permissions": "GUEST",
          "remark": "Token 驗證成功",
          "username": "luke1"
        },
        {
          "token": "..."
        }
      ],
      "timestamp": "2026-05-23 20:37:45"
    }

Request：登出

    {
        "username": "luke1"
    }

Response：

    {
      "code": "200 OK",
      "status": 200,
      "data": [
        {
          "created_date": "2026-05-23 20:40:27",
          "permissions": "GUEST",
          "remark": "Token 已登出",
          "username": "luke1"
        },
        {
          "token": ""
        }
      ],
      "timestamp": "2026-05-23 20:40:27"
    }

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
- *解析 JWT*
- *檢查 Redis*
- *查使用者（DB or Cache）*
## 11. 額外功能
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