package com.example.demo.Mapper;

import com.example.demo.Dto.Orderbackend.*;
import com.example.demo.Dto.Products.Product;
import com.example.demo.Dto.User.Orders;
import com.example.demo.Dto.User.UserData;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderbackendMapper {

    List<Map<String, Object>> selectUserUser(UserUser userUser);

    Integer selectQuotationsMax();

    @MapKey("username")
    Map<String, Map<String, Object>> selectDetailsData(UserData userData);

    void createQuotations(Quotations quotations);

    void createQuotationItems(QuotationItems quotationItems);

    List<Map<String, Object>> selectQuotationsData(UserData userData);

    void delQuotationItems(QuotationItems quotationItems);

    void delQuotations(Quotations quotations);

    List<Map<String, Object>> quotationsItemsProductsData(UserData userData);

    void updateQuotations(UserDataSend userDataDetails);

    void updateUserdataDetailIsActiveNotifications(UserDataSend userDataDetails);

    @MapKey("username")
    Map<String, Map<String, Object>> selectQuotationsDataSend(UserDataSend userDataSend);

    List<Map<String, Object>> selectOrdersData(Orders orders);

    @MapKey("product_id")
    Map<String, Map<String, Object>> selectProduct(Product product);

    void updateProducts(Product product);

    void updateOrders(Orders orders);

    Integer serialMax(Shipments shipments);

    void createShipments(Shipments shipments);

    List<Map<String, Object>> selectShipmentsData(Shipments shipments);

    void updateShipments(Shipments shipments);

    void createPayments(Payments payments);

    void updatePayments(Payments payments);

    @MapKey("tracking_number")
    Map<String, Map<String, Object>> selectProductsData(Shipments shipments);

    List<Map<String, Object>>  selectOrderItemData(Shipments shipments);

    List<Map<String, Object>>  orderItemDbData(String username);

    void updateUserdataDetailIsActiveNotificationsShow(String tracking_number, String username, String order_item);
}
