package com.springbootlearning.elderaisystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reminders")
@CrossOrigin
public class ReminderController {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 获取某个用户的所有未完成提醒
    @GetMapping("/list")
    public List<Map<String, Object>> getReminders(@RequestParam Long uid) {
        String SQL = "SELECT * FROM reminders WHERE user_id = ? AND is_completed = false ORDER BY next_remind_time ASC";
        return jdbcTemplate.queryForList(SQL, uid);
    }

    // 查询接口: 前端每10秒查询一次
    @GetMapping("/check")
    public List<Map<String, Object>> checkReminders(@RequestParam Long uid) {
        String CheckSQL = "SELECT reid, task_name, repeat_type FROM reminders " +
                "WHERE user_id = ? AND is_completed = false AND next_remind_time <= NOW()";
        return jdbcTemplate.queryForList(CheckSQL, uid);
    }

    // 标记提醒为已完成: 前端点击收到按钮后触发
    @PostMapping("/complete")
    public String completeReminder(@RequestParam Long reid) {
        String QuerySQL = "SELECT repeat_type FROM reminders WHERE reid = ?";
        Integer repeatType = jdbcTemplate.queryForObject(QuerySQL, Integer.class, reid);

        if (repeatType == null || repeatType == 0)
        {
            jdbcTemplate.update("UPDATE reminders SET is_completed = 1 WHERE reid = ?", reid);
        }
        else if(repeatType == 1)
        {
            jdbcTemplate.update("UPDATE reminders SET next_remind_time = DATE_ADD(next_remind_time, INTERVAL 1 DAY) WHERE reid = ?", reid);
        }
        else if(repeatType == 2)
        {
            jdbcTemplate.update("UPDATE reminders SET next_remind_time = DATE_ADD(next_remind_time, INTERVAL 1 WEEK) WHERE reid = ?", reid);
        }
        return "success";
    }

    // 取消提醒事项
    @PostMapping("/delete")
    public String deleteReminder(@RequestParam Long reid) {
        try {
            // 直接从数据库彻底删除该条记录
            String sql = "DELETE FROM reminders WHERE reid = ?";
            jdbcTemplate.update(sql, reid);
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }
}
