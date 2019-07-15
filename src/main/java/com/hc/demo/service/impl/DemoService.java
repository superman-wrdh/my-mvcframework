package com.hc.demo.service.impl;

import com.hc.framework.annnotation.MyServcie;

@MyServcie
public class DemoService implements com.hc.demo.service.DemoService {
    public String get(String name) {
        return name;
    }
}
