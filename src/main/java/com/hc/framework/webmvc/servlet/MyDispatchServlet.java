package com.hc.framework.webmvc.servlet;

import com.hc.framework.annnotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.logging.Handler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyDispatchServlet extends HttpServlet {

    private Properties contextConfig = new Properties();
    private List<String> classNames = new ArrayList<String>();
    private Map<String, Object> ioc = new HashMap<String, Object>();
    //private Map<String, Method> handMapping = new HashMap<String, Method>();
    private List<Handle> handMapping = new ArrayList<Handle>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //6,等待请求
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            resp.getWriter().write("500 server error");
            e.printStackTrace();
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        try {
            Handle handle = getHandle(req);
            if (handle == null) {
                resp.getWriter().write("404 Not Found");
                return;
            }

            Class<?>[] parameterTypes = handle.method.getParameterTypes();
            Object[] paramValues = new Object[parameterTypes.length];
            Map<String, String[]> params = req.getParameterMap();

            //TODO
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll("", "");

            }

            Integer reqIndex = handle.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = resp;
            Integer respIndex = handle.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[reqIndex] = resp;
            handle.method.invoke(handle.controller, paramValues);

        } catch (Exception e) {
            throw e;
        }

    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //从这里开始启动

        //1,加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2,扫描所有相关的类
        doScanner(contextConfig.getProperty("scanPackage"));

        //3,初始化相关的类
        doInstance();

        //=============Spring 核心初始化成功===============

        //4,自动注入

        doAutowired();

        //5,初始化HandleMapping

        iniHandleMapping();
        System.out.println("Spring init ...");
    }

    private void iniHandleMapping() {
        if (ioc.isEmpty()) return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(MyController.class)) continue;
            String baseUrl = "";

            if (clazz.isAnnotationPresent(MyController.class)) {
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(MyRequestMapping.class)) continue;

                MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);

                String regex = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");

                Pattern pattern = Pattern.compile(regex);
                handMapping.add(new Handle(pattern, entry.getValue(), method));

                //handMapping.put(methodUrl, method);

                System.out.println("mapping:" + regex + "," + method);
            }

        }

    }

    private void doAutowired() {
        if (ioc.isEmpty()) return;
        //循坏ioc容器中的所有类 然后对需要自动赋值的属性进行赋值
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(MyAutowired.class)) {
                    continue;
                }
                MyAutowired autowired = field.getAnnotation(MyAutowired.class);
                String beanNmae = autowired.value().trim();
                if ("".equals(beanNmae)) {
                    beanNmae = field.getType().getName();
                }

                // 暴力访问
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanNmae));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }

        }
    }

    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }

        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                //只有加了注解的类需要实例化
                if (clazz.isAnnotationPresent(MyController.class)) {
                    // key默认是类名搜字母小写
                    String beanName = lowFirstCase(clazz.getName());
                    ioc.put(beanName, clazz.newInstance());

                } else if (clazz.isAnnotationPresent(MyServcie.class)) {


                    //2,如果自定义名字话，优先使用自定义名字
                    MyServcie service = clazz.getAnnotation(MyServcie.class);
                    String beanName = service.value();
                    //1,默认搜字母小写
                    if ("".equals(beanName.trim())) {
                        beanName = lowFirstCase(clazz.getName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    //3,根据接口类型来赋值
                    for (Class<?> i : clazz.getInterfaces()) {
                        ioc.put(i.getName(), instance);
                    }

                } else {
                    continue;
                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                String className = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != is) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String lowFirstCase(String str) {
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private class Handle {
        protected Object controller;
        protected Method method;
        protected Pattern pattern;
        protected Map<String, Integer> paramIndexMapping;

        public Handle(Pattern pattern, Object controller, Method method) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            Annotation[][] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {

                for (Annotation a : pa[i]) {
                    if (a instanceof MyRequestParam) {
                        String paramName = ((MyRequestParam) a).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> type = parameterTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);
                }
            }

        }
    }

    private Handle getHandle(HttpServletRequest req) throws Exception {
        if (handMapping.isEmpty()) return null;
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        for (Handle handle : handMapping) {
            try {
                Matcher matcher = handle.pattern.matcher(url);
                if (!matcher.matches()) {
                    continue;
                }
                return handle;
            } catch (Exception e) {
                throw e;
            }

        }
        return null;
    }

}
