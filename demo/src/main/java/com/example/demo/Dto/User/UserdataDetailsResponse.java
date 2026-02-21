package com.example.demo.Dto.User;

import java.time.LocalDateTime;
import java.util.List;

public class UserdataDetailsResponse {

    private final String username;
    private final String message;
    private final String permissions;
    private final int status;
    private final List<String> order_item;
    private final LocalDateTime created_date;
    private final LocalDateTime updated_date;
    private final List<String> history;

    public UserdataDetailsResponse(UserdataDetails userdataDetails) {
        this.username = userdataDetails.getUsername();
        this.message = userdataDetails.getMessage();
        this.permissions = userdataDetails.getPermissions();
        this.status = userdataDetails.getStatus().value();
        this.order_item = userdataDetails.getOrder_item();
        this.created_date = userdataDetails.getCreated_date();
        this.updated_date = userdataDetails.getUpdated_date();
        this.history = userdataDetails.getHistory();
    }

    public String getUsername() {
        return username;
    }

    public String getMessage() {
        return message;
    }

    public String getPermissions() {
        return permissions;
    }

    public int getStatus() {
        return status;
    }

    public List<String> getOrder_item() {
        return order_item;
    }

    public LocalDateTime getCreated_date() {
        return created_date;
    }

    public LocalDateTime getUpdated_date() {
        return updated_date;
    }

    public List<String> getHistory() {
        return history;
    }

}
