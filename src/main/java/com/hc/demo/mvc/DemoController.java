package com.hc.demo.mvc;

import com.hc.demo.service.DemoService;
import com.hc.framework.annnotation.MyAutowired;
import com.hc.framework.annnotation.MyController;
import com.hc.framework.annnotation.MyRequestMapping;
import com.hc.framework.annnotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyRequestMapping("/demo")
public class DemoController {

    @MyAutowired
    private DemoService demoService;

    @MyRequestMapping("query")
    public void query(HttpServletRequest req, HttpServletResponse resp, @MyRequestParam("name") String name) {
        String result = demoService.get(name);
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
