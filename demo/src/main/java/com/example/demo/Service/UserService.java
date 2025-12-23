package com.example.demo.Service;

import java.util.List;

public interface UserService {

    boolean createUser(String name, String data);

    Object query(String name);

    void delete(String name);
}
