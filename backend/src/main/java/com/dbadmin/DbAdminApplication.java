package com.dbadmin;

import com.dbadmin.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class DbAdminApplication {

    private static final Logger log = LoggerFactory.getLogger(DbAdminApplication.class);
    public static void main(String[] args) {
        SpringApplication.run(DbAdminApplication.class, args);

        // 打印访问地址：
        log.info("访问地址：http://localhost:8080/index.html");
    }
}