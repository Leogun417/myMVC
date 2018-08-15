package com.servlet;

import com.MyViewResolver;
import com.annotation.MyController;
import com.annotation.MyRequestMapping;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class MyDispatcherServlet extends HttpServlet {
    //模拟IOC容器，保存Controller实例对象
    private Map<String,Object> iocContainer = new HashMap<String,Object>();
    //保存handler映射
    private Map<String,Method> handlerMapping = new HashMap<String,Method>();
    //自定视图解析器
    private MyViewResolver myViewResolver;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String controllerUri = req.getRequestURI().split("/")[2];
        String methodUri = req.getRequestURI().split("/")[3];
        Method method = handlerMapping.get(methodUri);
        Object obj = iocContainer.get(controllerUri);
        try {
            String value = (String) method.invoke(obj);
            String jspUrl = myViewResolver.jspMapping(value);
            req.getRequestDispatcher(jspUrl).forward(req, resp);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //扫描指定包下的controller并将其实例化
        scanController(config);
        //保存handler映射
        initHandlerMapping();
        //加载试图解析器
        loadViewResolver(config);
    }

    private void loadViewResolver(ServletConfig config) {
        SAXReader reader = new SAXReader();
        String path = MyDispatcherServlet.class.getClassLoader().getResource("") + config.getInitParameter("contextConfigLocation");
        try {
            Document document = reader.read(path);
            Element rootElement = document.getRootElement();
            Iterator iterator = rootElement.elementIterator();
            while (iterator.hasNext()) {
                Element element = (Element) iterator.next();
                if (element.getName().equals("bean")) {
                    String className = element.attributeValue("class");
                    Class<?> clazz = Class.forName(className);
                    MyViewResolver resolver = (MyViewResolver) clazz.newInstance();
                    Method setPrefix = clazz.getMethod("setPrefix", String.class);
                    Method setSuffix = clazz.getMethod("setSuffix", String.class);
                    Iterator node = element.elementIterator();
                    while (node.hasNext()) {
                        Element next = (Element) node.next();
                        String name = next.attributeValue("name");
                        String value = next.attributeValue("value");
                        if ("prefix".equals(name)) {
                            setPrefix.invoke(resolver, value);
                        } else if ("suffix".equals(name)) {
                            setSuffix.invoke(resolver, value);
                        }
                    }
                    myViewResolver = resolver;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initHandlerMapping() {
        for (String controllerName : iocContainer.keySet()) {
            Object controller = iocContainer.get(controllerName);
            Method[] methods = controller.getClass().getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(MyRequestMapping.class)) {
                    MyRequestMapping annotation = method.getAnnotation(MyRequestMapping.class);
                    String methodName = annotation.value().substring(1);
                    handlerMapping.put(methodName, method);
                }
            }
        }
    }

    private void scanController(ServletConfig config) {
        SAXReader reader = new SAXReader();
        String path = MyDispatcherServlet.class.getClassLoader().getResource("") + config.getInitParameter("contextConfigLocation");
        Document document = null;
        try {
            document = reader.read(path);
            Element rootElement = document.getRootElement();
            Iterator iterator = rootElement.elementIterator();
            while (iterator.hasNext()) {
                Element element = (Element) iterator.next();
                if ("component-scan".equals(element.getName())) {
                    String packageName = element.attributeValue("base-package");
                    List<String> classNames = getClassNames(packageName);
                    for (String className : classNames) {
                        Class<?> clazz = Class.forName(className);
                        if (clazz.isAnnotationPresent(MyController.class)) {
                            MyRequestMapping annotation = clazz.getAnnotation(MyRequestMapping.class);
                            String value = annotation.value().substring(1);
                            iocContainer.put(value, clazz.newInstance());
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> getClassNames(String packageName) {
        ArrayList<String> classNames = new ArrayList<>();
        URL url = MyDispatcherServlet.class.getClassLoader().getResource(packageName.replace(".", "/"));
        if (url != null) {
            File file = new File(url.getPath());
            File[] childrenFiles = file.listFiles();
            for (File child : childrenFiles) {
                classNames.add(packageName + "." + child.getName().replace(".class", ""));
            }
        }
        return classNames;
    }
}
