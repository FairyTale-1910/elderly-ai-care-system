package com.springbootlearning.elderaisystem.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin
public class ProfileController {

    @Autowired
    JdbcTemplate jdbcTemplate;

    /**
     * 1. 获取老人的健康档案（用于前端打开弹窗时显示旧数据）
     */
    @GetMapping("get")
    public Map<String, Object> getProfile(@RequestParam int uid) {
        try {
            String sql = "SELECT u.username, p.* FROM users u LEFT JOIN user_profiles p ON u.uid = p.uid WHERE u.uid = ?";
            return jdbcTemplate.queryForMap(sql, uid);
        } catch(EmptyResultDataAccessException e) {
            return Map.of("status", "empty");
        }
    }

    /**
     * 2. 保存或更新老人的健康档案
     */
    @PostMapping("/save")
    @Transactional
    public String saveProfile(@RequestBody Map<String, Object> data) {
        try {
            if (data.get("name") != null && !data.get("name").toString().isEmpty()) {
                String sqlUser = "UPDATE users SET username = ? WHERE uid = ?";
                jdbcTemplate.update(sqlUser, data.get("name"), data.get("uid"));
            }

            // 如果 uid 不存在就插入新数据；如果 uid 已经存在，就只更新后面这几个字段！
            String sql = "INSERT INTO user_profiles (uid, age, gender, dialect, medical_history, physical_condition, dietary_preference, emergency_contact) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "age = VALUES(age), gender = VALUES(gender), dialect = VALUES(dialect), medical_history = VALUES(medical_history), " +
                    "physical_condition = VALUES(physical_condition), dietary_preference = VALUES(dietary_preference), " +
                    "emergency_contact = VALUES(emergency_contact)";

            jdbcTemplate.update(sql, data.get("uid"), data.get("age"), data.get("gender"), data.get("dialect"),
                    data.get("medical_history"), data.get("physical_condition"),
                    data.get("dietary_preference"), data.get("emergency_contact"));
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

}
