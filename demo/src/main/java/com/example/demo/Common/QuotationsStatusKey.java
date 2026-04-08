package com.example.demo.Common;

import java.util.Map;

public class QuotationsStatusKey {

    // *報價（quotations） *status -- estimate（預估） / sent（已送出） / accepted（接受） / rejected（拒絕）
    public static final Map<String, String> quotationsKey = Map.of(
            "estimate", "預估",
            "sent", "已送出",
            "accepted", "接受",
            "rejected", "拒絕"
    );

}
