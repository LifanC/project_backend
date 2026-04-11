package com.example.demo.Mapper;

import com.example.demo.Dto.User.Orders;
import com.example.demo.Dto.Orderbackend.Quotations;
import com.example.demo.Dto.Orderbackend.UserDataSend;
import com.example.demo.Dto.User.QuotationsProduct;
import com.example.demo.Dto.User.UserData;
import com.example.demo.Dto.User.UserdataDetails;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;

import java.util.*;

@Mapper
public interface UserMapper {

    @MapKey("username")
    Map<String, Map<String, Object>> select(UserData user);

    void create(UserData user);

    void delete(UserData userData);

    void createUserdataDetail(UserdataDetails userdataDetail);

    @MapKey("username")
    Map<String, Map<String, Object>> selectUserdataDetail(UserdataDetails userdataDetails);

    void updateUserdataDetail(UserdataDetails userdataDetail);

    void deleteUserdataDetail(UserdataDetails userdataDetails);

    List<String> queryUserName();

    void updateUserdataDetailIsActive(String username);

    List<Map<String, Object>> userdataDetailsDataId(QuotationsProduct quotationsProduct);

    List<Map<String, Object>> userdataDetailsData(QuotationsProduct quotationsProduct);

    void updateQuotations(UserDataSend userDataDetails);

    @MapKey("username")
    Map<String, Map<String, Object>> selectQuotations(Quotations quotations);

    Integer selectOrdersItemsMax();

    void createOrders(Orders orders);

    List<Map<String, Object>> getOrdersQuotations(Quotations quotations);
}
