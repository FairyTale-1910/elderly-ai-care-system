package com.springbootlearning.elderaisystem.controller;

import com.springbootlearning.elderaisystem.service.AIService;
import com.springbootlearning.elderaisystem.service.VoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

    @Autowired
    @Qualifier("xunfeiVoiceService")
    private VoiceService voiceService;

    @PostMapping("/send") // POST 请求接口，用于提交数据（对话内容）
    public Map<String, Object> sendMessage(@RequestParam Long uid, @RequestBody Map<String, String> body)
    {
        // @RequestParam Long uid: 从 URL 链接里获取 uid（例如 ?uid=1）
        // @RequestBody Map<String, String> body: 从请求体里获取 JSON 数据（对话内容）

        Map<String, Object> response = new HashMap<>();
        try {
            String userContent = body.get("content");

            // 1. 获取或生成 session_id
            // 核心逻辑：如果前端传了 session_id 就用旧的（继续对话），没传就生成新的（新开对话）
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
            String cleanReply = aiReply;
            String detectedEmotion = null;

            // 5.1 提取[EMOTION]负面情绪
            if(cleanReply.contains("[EMOTION]"))
            {
                try {
                    int startIndex = cleanReply.indexOf("[EMOTION]") + 9;
                    int endIndex = cleanReply.indexOf("[/EMOTION]");
                    if(startIndex < endIndex)
                    {
                        detectedEmotion = cleanReply.substring(startIndex, endIndex).trim();
                        System.out.println("🚨 【系统告警】检测到老人负面情绪：" + detectedEmotion);

                        // 后续补充：将负面情绪信息传递到子女端
                    }
                } catch (Exception e) {
                    System.err.println("❌ 解析情绪标签失败");
                }
                // 把提取完的标签从文本中彻底抹除
                cleanReply = cleanReply.replaceAll("\\[EMOTION\\].*?\\[/EMOTION\\]", "");
            }

            // 5.2 拦截提醒的标签
            if(cleanReply.contains("[REMINDER_TASK]"))
            {
                try {
                    int startIndex = cleanReply.indexOf("[REMINDER_TASK]") + 15;
                    int endIndex = cleanReply.indexOf("[/REMINDER_TASK]");
                    if (startIndex < endIndex) {
                        String taskData = cleanReply.substring(startIndex, endIndex);
                        String[] parts = taskData.split("\\|");

                        if (parts.length >= 3) {
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
                            System.out.println("✅ 成功创建提醒任务：" + taskName);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("❌ 解析提醒标签失败");
                }
                cleanReply = cleanReply.replaceAll("\\[REMINDER_TASK\\].*?\\[/REMINDER_TASK\\]", "");
            }

            cleanReply = cleanReply.trim();

            if (detectedEmotion != null) {
                String updateElderUserSQL = "UPDATE chat_history SET emotion = ? WHERE session_id = ? AND role = 'user' ORDER BY created_at DESC LIMIT 1";
                jdbcTemplate.update(updateElderUserSQL, detectedEmotion, session_id);
            }

            // 6. 将 AI 的回复存入数据库中
            String insertAiSQL = "INSERT INTO chat_history(session_id, user_id, content, role, emotion) VALUES (?, ?, ?, ?, ?)";
            jdbcTemplate.update(insertAiSQL, session_id, uid, cleanReply, "assistant", null);

            // 7.语音合成
            String audioBase64 = voiceService.textToSpeech(cleanReply);

            // 8.返回结果
            response.put("status", "success");
            response.put("response", cleanReply);
            response.put("session_id", session_id);
            response.put("emotion", detectedEmotion);
            response.put("audioBase64", audioBase64);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", "服务器错误");
        }
        return response;
    }

}
