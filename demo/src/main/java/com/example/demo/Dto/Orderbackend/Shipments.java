package com.example.demo.Dto.Orderbackend;

import java.math.BigDecimal;
import java.util.List;

public class Shipments {

    private BigDecimal order_id;

    private String username;

    private String status;

    private String prefix;

    private String date_part;

    private String serial;

    private String tracking_number;

    private List<String> quotationsStatuss;

    private List<String> ordersStatuss;

    private List<String> shipmentsStatuss;

    public Shipments() {
    }

    public Shipments(BigDecimal order_id) {
        this.order_id = order_id;
    }

    public BigDecimal getOrder_id() {
        return order_id;
    }

    public void setOrder_id(BigDecimal order_id) {
        this.order_id = order_id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getDate_part() {
        return date_part;
    }

    public void setDate_part(String date_part) {
        this.date_part = date_part;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getTracking_number() {
        return tracking_number;
    }

    public void setTracking_number(String tracking_number) {
        this.tracking_number = tracking_number;
    }

    public List<String> getQuotationsStatuss() {
        return quotationsStatuss;
    }

    public void setQuotationsStatuss(List<String> quotationsStatuss) {
        this.quotationsStatuss = quotationsStatuss;
    }

    public List<String> getOrdersStatuss() {
        return ordersStatuss;
    }

    public void setOrdersStatuss(List<String> ordersStatuss) {
        this.ordersStatuss = ordersStatuss;
    }

    public List<String> getShipmentsStatuss() {
        return shipmentsStatuss;
    }

    public void setShipmentsStatuss(List<String> shipmentsStatuss) {
        this.shipmentsStatuss = shipmentsStatuss;
    }
}
