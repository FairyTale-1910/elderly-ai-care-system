package com.springbootlearning.elderaisystem.controller;

import com.springbootlearning.elderaisystem.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin // 允许跨域，方便前端调用
public class ChatController {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AIService aiService;

    @PostMapping("/send") // POST 请求接口，用于提交数据（对话内容）
    public Map<String, Object> sendMessage(@RequestParam Long uid, @RequestBody Map<String, String> body)
    {
        // @RequestParam Long uid: 从 URL 链接里获取 uid（例如 ?uid=1）
        // @RequestBody Map<String, String> body: 从请求体里获取 JSON 数据（对话内容）

        Map<String, Object> response = new HashMap<>();
        try {
            String userContent = body.get("content");

            // 1. 获取或生成 sessionId
            // 核心逻辑：如果前端传了 sessionId 就用旧的（继续对话），没传就生成新的（新开对话）
            String session_id = body.get("sessionId");
            if (session_id == null || session_id.trim().isEmpty()) {
                session_id = UUID.randomUUID().toString().substring(0, 8);
            }

            // 2.将老人的话存入数据库中
            String insertSQL = "INSERT INTO chat_history(session_id, user_id, content, role) VALUES (?, ?, ?, ?)";
            jdbcTemplate.update(insertSQL, session_id, uid, userContent, "user");

            // 3.记忆提取
            String QuerySQL = "SELECT role, content FROM (" +
                              "SELECT role, content, created_at FROM chat_history WHERE" +
                              " session_id = ? ORDER BY created_at DESC LIMIT 10)" +
                              " sub ORDER BY created_at ASC";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(QuerySQL, session_id);

            List<Map<String, String>> ChatHistory = new ArrayList<>();
            for(Map<String, Object> row : rows)
            {
                ChatHistory.add(Map.of("role", (String)row.get("role"), "content", (String)row.get("content")));
            }

            // 4.获取AI的回复
            String aiReply = aiService.getAIResponse(ChatHistory);

            // 5.拦截提醒的关键词
            if(aiReply.contains("[REMINDER_TASK]"))
            {
                // 提取标签内容
                String taskData = aiReply.substring(aiReply.indexOf("[REMINDER_TASK]") + 15, aiReply.indexOf("[/REMINDER_TASK]"));
                String[] parts = taskData.split("\\|");

                if(parts.length >= 3)
                {
                    String taskName = parts[0].trim();
                    String fullDateTime = parts[1].trim();
                    String frequencyStr = parts[2].trim();
                    String timeOnly = fullDateTime.substring(11);

                    int repeatTypeInt = 0; // 默认 0 代表 ONCE
                    if ("DAILY".equals(frequencyStr)) {
                        repeatTypeInt = 1;
                    } else if ("WEEKLY".equals(frequencyStr)) {
                        repeatTypeInt = 2;
                    }

                    // 存入数据库
                    String SQL = "INSERT INTO reminders (user_id, task_name, remind_time, next_remind_time, repeat_type) VALUES (?, ?, ?, ?, ?)";
                    jdbcTemplate.update(SQL, uid, taskName, timeOnly, fullDateTime, repeatTypeInt);
//                    System.out.println(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    System.out.println("✅ 成功存入数据库！下次执行时间：" + fullDateTime);
                }

                // 将标签从回复中删掉
                aiReply = aiReply.replaceAll("\\[REMINDER_TASK\\].*?\\[/REMINDER_TASK\\]", "").trim();
            }

            // 6.将AI的回复存入数据库中
            String insertAiSQL = "INSERT INTO chat_history(session_id, user_id, content, role) VALUES (?, ?, ?, ?)";
            jdbcTemplate.update(insertAiSQL, session_id, uid, aiReply, "assistant");

            // 7.返回结果
            response.put("status", "success");
            response.put("response", aiReply);
            response.put("session_id", session_id);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", "服务器错误");
        }
        return response;
    }

}
