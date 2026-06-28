package com.example.demo.Security.Annotation;

import com.example.demo.Aspect.Permissions;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CheckRole {
    Permissions value();
}
