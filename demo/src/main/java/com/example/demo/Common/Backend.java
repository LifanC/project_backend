package com.example.demo.Common;

public enum Backend {
    // *報價（quotations） *status -- draft（草稿） / sent（已送出） / accepted（客戶接受） / rejected（拒絕）
    STATUS_QUOTATIONS_DRAFT("draft"),
    STATUS_QUOTATIONS_SENT("sent"),
    STATUS_QUOTATIONS_ACCEPTED("accepted"),
    STATUS_QUOTATIONS_REJECTED("rejected"),
    // *訂單（orders） *status -- pending（待處理） / confirmed（已確認） / cancelled（取消）
    STATUS_ORDERS_PENDING("pending"),
    STATUS_ORDERS_CONFIRMED("confirmed"),
    STATUS_ORDERS_CANCELLED("cancelled"),
    // *出貨（shipments） *status -- preparing（備貨中） / shipped（已出貨） / delivered（已送達）
    STATUS_SHIPMENTS_PENDING("preparing"),
    STATUS_SHIPMENTS_SHIPPED("shipped"),
    STATUS_SHIPMENTS_DELIVERED("delivered"),
    // *收款（payments）
    // *status -- unpaid（未付） / partial（部分） / paid（已付）
    // *method -- cash（現金） / credit_card（信用卡） / transfer（轉帳）
    STATUS_PAYMENTS_UNPAID("unpaid"),
    STATUS_PAYMENTS_PARTIAL("partial"),
    STATUS_PAYMENTS_PAID("unpaid"),
    METHOD_PAYMENTS_CASH("cash"),
    METHOD_PAYMENTS_CREDIT_CARD("credit_card"),
    METHOD_PAYMENTS_TRANSFER("transfer"),
    ;

    private final String backend;

    Backend(String backend) {
        this.backend = backend;
    }

    public String getBackend() {
        return backend;
    }
}
