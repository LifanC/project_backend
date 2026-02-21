package com.example.demo.Aspect;

public enum Permissions {
    USER_ITEM_QUERY("user:item:query"),
    ORDER_ITEM_QUERY("order:item:query"),
    ORDER_ITEM_CREATE("order:item:create"),
    ORDER_ITEM_UPDATE("order:item:update"),
    ORDER_ITEM_DELETE("order:item:delete"),
    ORDER_ITEM_HISTORY("order:item:history");

    private final String permission;

    Permissions(String permission) {
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }
}
