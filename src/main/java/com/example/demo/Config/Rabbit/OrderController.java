package com.example.demo.Config.Rabbit;

import com.example.demo.Service.Rabbitmq.RabbitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {


    private final RabbitService producer;


    public OrderController(RabbitService producer) {
        this.producer = producer;
    }


    @GetMapping
    public String createOrder() {

        /*
         * Producer 負責產生訊息，Exchange 負責分流，Queue 負責保存，Consumer 負責處理。
         * RabbitMQ 的核心思想就是：
         * 讓系統透過訊息溝通，而不是直接互相依賴，提升可靠性、擴展性與非同步處理能力。
         *
         *
         *
         * 元件	責任
         * Controller	接收 HTTP Request、回 Response
         * Producer	把工作送進 RabbitMQ
         * Exchange	分派訊息
         * Queue	保存任務
         * Consumer	執行背景工作
         * Service	商業邏輯
         * Model/Repository	資料存取
         * Database	保存資料
         * View	呈現結果
         *
         *
         *                  Client
         *                     |
         *                     v
         *               Controller
         *                     |
         *                     v
         *               Order Service
         *                     |
         *                     v
         *               RabbitMQ Producer
         *                     |
         *                     v
         *               Exchange
         *                     |
         *                     v
         *               Queue
         *                     |
         *                     v
         *               Order Consumer
         *                     |
         *                     v
         *               Order Service
         *                     |
         *                     v
         *               Repository
         *                     |
         *                     v
         *               Database
         *
         * 三、常見操作訊息
         * Customer
         * 客戶建立成功
         * 客戶更新成功
         * 客戶已刪除
         * 找不到客戶
         * Product
         * 商品建立成功
         * 商品更新成功
         * 商品已下架
         * 庫存不足
         * Quotation
         * 報價單建立成功
         * 報價單已送出
         * 報價已接受
         * 報價已拒絕
         * 此報價無法修改
         * 已由此報價建立訂單
         * Order
         * 訂單建立成功
         * 訂單確認完成
         * 訂單已取消
         * 訂單不可重複確認
         * Shipment
         * 開始備貨
         * 出貨完成
         * 配送完成
         * 尚未完成備貨
         * Payment
         * 付款成功
         * 付款失敗
         * 付款金額不足
         * 已完成付款
         * 仍有未付款項
         * 四、驗證訊息（Validation）
         * 客戶不存在
         * 商品不存在
         * 報價不存在
         * 訂單不存在
         * 出貨單不存在
         * 付款紀錄不存在
         * 商品數量不可小於 1
         * 價格不可小於 0
         * 付款金額不可小於 0
         * 日期格式錯誤
         * 請選擇客戶
         * 請選擇商品
         * 請輸入付款方式
         * 請輸入付款金額
         * 五、流程限制訊息
         *
         * 例如你的流程：
         *
         * Customer
         *     ↓
         * Quotation
         *     ↓
         * Order
         *     ↓
         * Shipment
         *     ↓
         * Payment
         *
         * 可以設計一些商業規則提示：
         *
         * 報價尚未送出，無法建立訂單
         * 僅已接受的報價可以建立訂單
         * 訂單已取消，無法出貨
         * 訂單尚未確認，無法出貨
         * 尚未完成出貨，不可完成收款
         * 付款金額超過訂單金額
         * 訂單已完成付款
         * 六、RabbitMQ／背景工作訊息
         *
         * Producer
         *
         * 任務已加入佇列
         * 開始建立報價 PDF
         * 開始寄送 Email
         * 開始同步庫存
         *
         * Consumer
         *
         * 開始處理任務
         * 任務處理完成
         * 任務處理失敗
         * 正在重試
         * 重試成功
         *
         * Queue
         *
         * 等待處理
         * 處理中
         * 完成
         * 失敗
         * 七、建議的中文用詞統一
         *
         * 建議全系統維持一致的狀態命名：
         *
         * 英文	建議中文
         * Draft	草稿
         * Pending	待確認
         * Processing	處理中
         * Preparing	備貨中
         * Confirmed	已確認
         * Sent	已送出
         * Accepted	已接受
         * Rejected	已拒絕
         * Cancelled	已取消
         * Shipped	已出貨
         * Delivered	已送達
         * Unpaid	未付款
         * Partial	部分付款
         * Paid	已付款
         * Failed	失敗
         * Completed	已完成
         *
         * */

        producer.send("TEST");

        return "sent";
    }
}