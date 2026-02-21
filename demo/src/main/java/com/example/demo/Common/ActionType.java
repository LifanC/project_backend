package com.example.demo.Common;

public enum ActionType {
    INSERT("insert"),
    UPDATE("update"),
    DELETE("delete");

    private final String actionType;

    ActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getActionType() {
        return actionType;
    }
}
