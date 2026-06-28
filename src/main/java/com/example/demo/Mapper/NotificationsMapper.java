package com.example.demo.Mapper;

import com.example.demo.Dto.User.UserData;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface NotificationsMapper {

    List<Map<String, Object>> selectUserdataDetails();

    List<Map<String, Object>> selectQuotations(UserData userData);

    List<Map<String, Object>> selectOrders();

    List<Map<String, Object>> selectShipments(UserData userData);

    List<Map<String, Object>> selectPayments(UserData userData);

    List<Map<String, Object>> selectShipmentsOrderbackend();

    List<Map<String, Object>> selectPaymentsOrderbackend();

    List<Map<String, Object>> selectShipmentsUser(UserData userData);

}
