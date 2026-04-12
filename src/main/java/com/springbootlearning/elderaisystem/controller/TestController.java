package com.springbootlearning.elderaisystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/test-db")
    public String testDb() {
        try {
            String sql = "INSERT INTO users (username, role) VALUES (?, ?)";
            jdbcTemplate.update(sql, "测试张大爷", "ELDER");
            return "数据库连接成功，已自动插入一条测试数据！";
        } catch (Exception e) {
            return "数据库连接失败：" + e.getMessage();
        }
    }
}