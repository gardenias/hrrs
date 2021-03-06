package com.vlkan.hrrs.example.spring;

import com.vlkan.hrrs.servlet.HrrsFilter;
import com.vlkan.hrrs.servlet.HrrsServlet;
import com.vlkan.hrrs.servlet.base64.Base64HrrsFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.File;
import java.io.IOException;

@SpringBootApplication
public class HelloApplication {

    public static void main(String[] args) {
        run(args);
    }

    public static ConfigurableApplicationContext run(String[] args) {
        return SpringApplication.run(HelloApplication.class, args);
    }

    @Bean
    public HrrsFilter provideHrrsFilter() throws IOException {
        File writerTargetFile = File.createTempFile("hrrs-spring-records-", ".csv");
        return new Base64HrrsFilter(writerTargetFile);
    }

    @Bean
    public ServletRegistrationBean provideHrrsServlet() {
        HrrsServlet hrrsServlet = new HrrsServlet();
        return new ServletRegistrationBean(hrrsServlet, "/hrrs");
    }

}
