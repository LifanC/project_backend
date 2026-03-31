package com.example.demo.Mapper;

import com.example.demo.Dto.Products.Product;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductMapper {

    String selectMaxProductId();

    void create(Product product);

    List<Map<String, Object>> select(Product product);

    void update(Product product);

    void delete(Product product);
}
