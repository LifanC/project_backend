package com.example.demo.Aspect;

public enum Permissions {
    USER_ITEM_QUERY("user:item:query"),
    CAR_ITEM_QUERY("car:item:query"),
    CAR_ITEM_CREATE("car:item:create"),
    CAR_ITEM_UPDATE("car:item:update"),
    CAR_ITEM_DELETE("car:item:delete"),
    CAR_ITEM_HISTORY("car:item:history"),
    USER_ITEM_TOKEN("user:item:token"),
    ORDERBACKEND_ITEM_TOKEN("orderbackend:item:token");

    private final String permission;

    Permissions(String permission) {
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }
}
