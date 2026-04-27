package com.springbootlearning.elderaisystem.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin
@RequestMapping("/api/session")
public class SessionController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    /**
     * 获取左侧对话的标题
     */
    @GetMapping("/list")
    public List<Map<String, Object>> getSessionList(@RequestParam Long uid) {
        String sql = "SELECT session_id, title FROM chat_sessions WHERE user_id = ? ORDER BY updated_at DESC";
        return jdbcTemplate.queryForList(sql, uid);
    }

    /**
     * 2. 获取某个对话的历史记录气泡
     */
    @GetMapping("/history")
    public List<Map<String, Object>> getSessionHistory(@RequestParam("sessionId") String session_id) {
        String sql = "SELECT role, content FROM chat_history WHERE session_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.queryForList(sql, session_id);
    }


}
