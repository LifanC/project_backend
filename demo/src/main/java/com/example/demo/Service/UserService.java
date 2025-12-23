package com.example.demo.Service;

import java.util.List;

public interface UserService {

    boolean createUser(String name, String data);

    Object query(String name);

    int delete(String name);
}
