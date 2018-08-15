package com.controller;

import com.annotation.MyController;
import com.annotation.MyRequestMapping;

@MyController
@MyRequestMapping(value = "/test")
public class TestController {

    @MyRequestMapping(value = "/index")
    public String index() {
        return "/index";
    }
}
