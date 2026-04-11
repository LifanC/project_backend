package com.example.demo.Common;

import java.util.Map;

public class StatusKey {

    // *報價（quotations） *status -- estimate（預估） / sent（已送出） / accepted（接受） / rejected（拒絕）
    public static final Map<String, String> quotationsStatusKey = Map.of(
            "estimate", "預估",
            "sent", "已送出",
            "accepted", "接受",
            "rejected", "拒絕"
    );
    // *訂單（orders） *status -- pending（待處理） / confirmed（已確認） / cancelled（取消）
    public static final Map<String, String> ordersStatusKey = Map.of(
            "pending", "待處理",
            "confirmed", "已確認",
            "cancelled", "取消"
    );
    // *出貨（shipments） *status -- preparing（備貨中） / shipped（已出貨） / delivered（已送達）
    public static final Map<String, String> shipmentsStatusKey = Map.of(
            "preparing", "備貨中",
            "shipped", "已出貨",
            "delivered", "已送達"
    );
    // *收款（payments）
    // *status -- unpaid（未付） / partial（部分） / paid（已付）
    public static final Map<String, String> paymentsStatusKey = Map.of(
            "unpaid", "未付",
            "partial", "部分",
            "paid", "已付"
    );
    // *收款（payments）
    // *method -- cash（現金） / credit_card（信用卡） / transfer（轉帳）
    public static final Map<String, String> paymentsMethodKey = Map.of(
            "cash", "現金",
            "credit_card", "信用卡",
            "transfer", "轉帳"
    );
}
