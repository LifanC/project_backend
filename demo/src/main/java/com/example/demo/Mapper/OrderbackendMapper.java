package com.example.demo.Mapper;

import com.example.demo.Dto.Orderbackend.QuotationItems;
import com.example.demo.Dto.Orderbackend.Quotations;
import com.example.demo.Dto.Orderbackend.UserDataSend;
import com.example.demo.Dto.Orderbackend.UserUser;
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

    @MapKey("username")
    Map<String, Map<String, Object>> selectQuotationsDataSend(UserDataSend userDataSend);
}
