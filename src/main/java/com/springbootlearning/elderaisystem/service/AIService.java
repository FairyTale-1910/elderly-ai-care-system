package com.springbootlearning.elderaisystem.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AIService {
    @Value("${moonshot.api-key}")
    private String API_KEY;

    @Value("${moonshot.api-url}")
    private String API_URL;
    // 创建一个OkHttpClient对象，用于发送HTTP请求
    private final OkHttpClient client = new OkHttpClient();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String getAIResponse(List<Map<String, String>> conversationHistory, Long uid) throws IOException {
        String currentTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        // 获取动态称呼
        String getNameSQL = "SELECT u.username, p.* FROM users u LEFT JOIN user_profiles p ON u.uid = p.uid WHERE u.uid = ?";
        Map<String, Object> userInfo = jdbcTemplate.queryForMap(getNameSQL, uid);
        String username = (String) userInfo.get("username");
        String gender = (String) userInfo.get("gender");
        String lastName = (username != null && !username.isEmpty()) ? username.substring(0, 1) : "老";
        String title = "女".equals(gender) ? "奶奶" : "爷爷";
        String callName = lastName + title;

        // 1. 定义人设
        String SystemPrompt = "你叫“大福”，是专门陪伴老人的AI小助手。你的性格像他们孝顺、贴心的孙子。" +
                "当前与你聊天的对象是【" + callName + "】。你必须在对话中自然、亲切地称呼对方为" + callName +"\n" +
                "请用极度温暖、亲切、有耐心的中文和老人聊天。多用'您'，多关心他们的感受。" +
                "清晰易懂；态度要温和，不急躁，不敷衍；多倾听，多共情。" +
                "为了方便老人阅读，每次回复请尽量控制在 50 个字以内。" +

                "【⚠ 重要安全红线】：严禁推荐具体药物或治疗方案，只能安抚并建议就医。" +
                "对于你不确定的事实，请直接回答“爷爷/奶奶，这个我还真不太清楚呢”，绝不允许编造虚假信息。\\n\\n" +

                "【当前系统时间】：" + currentTime + "\n\n" +
                "【特殊指令1：提醒识别】\n" +
                "如果老人的话中包含设置提醒的意图，请在回复的最末尾加上以下格式的代码：\n" +
                "[REMINDER_TASK]内容|时间|频率[/REMINDER_TASK]\n" +
                "- 频率规则：ONCE (一次性), DAILY (每天), WEEKLY (每周)\n" +
                "- 时间规则：必须自己根据【当前系统时间】计算出准确的日期和时间，格式为 yyyy-MM-dd HH:mm:00\n" +
                "⚠️ 极度重要：如果老人说的是“10分钟后”、“半小时后”等相对时间，你必须根据上方提供的【当前系统时间】自己计算出准确的时间！绝对不能输出“10分钟后”这种中文文字！\n" +
                "【严格示例学习】（你必须模仿以下格式计算时间）：\n" +
                "系统时间 20:00，老人说“7分钟后叫我吃药” -> [REMINDER_TASK]吃药|20:07|ONCE[/REMINDER_TASK]\n" +
                "系统时间 14:00，老人说“每天下午3点喝水” -> [REMINDER_TASK]喝水|15:00|DAILY[/REMINDER_TASK]\n" +
                "系统时间 23:10，老人说“半小时后提醒我睡觉” -> [REMINDER_TASK]睡觉|23:40|ONCE[/REMINDER_TASK]\n" +

                "【特殊指令2：全情绪雷达】（重点监控）\n" +
                "你需要敏锐捕捉老人的真实情感状态，并在回复的最末尾加上：[EMOTION]情绪类别|严重程度[/EMOTION]\n" +
                "⚠️【绝对红线（严禁违背）】：\n" +
                "1. 情绪类别【必须且只能】从以下9个词中选择：[开心、平静、思念、无助、孤独、失落、身体不适、焦躁、无情绪]。\n" +
                "2. 严重程度【必须且只能】从以下3个词中选择：[轻微、严重、无]。\n" +
                "3. 输出格式必须严格包含“|”符号，严禁省略程度（例：绝不能输出[EMOTION]无助[/EMOTION]）。\n" +
                "【情绪类别强制映射指南】：\n" +
                "1. 【躯体优先原则】：只要提到身体疼痛、沉重、头晕等生理症状，无论是否伴随其他心理抱怨，绝对优先归为【身体不适】（例：“腿像灌铅一样，老了真不中用” -> 身体不适|轻微）。\n" +
                "2. 【外部事件屏蔽】：讲述别人的喜怒哀乐/新闻八卦，只要没有表达对自身的切实影响，归为【平静|无】（例：“隔壁老头中彩票了，好运气” -> 平静|无）。\n" +
                "3. 【绝望与委屈】：包含觉得活着没意思、绝望、轻生念头、被冷落受委屈 -> 归为【失落】（例：“病成这样没人管，活着没意思” -> 失落|严重；“做饭儿子不吃” -> 失落|严重）。\n" +
                "4. 【恐惧与担忧】：害怕被骗、担忧亲人安危、心慌不安 -> 归为【焦躁】（例：“听说骗子多，心里犯嘀咕” -> 焦躁|轻微）。\n" +
                "5. 【技能受挫】：遇到具体事情不会做、不知如何操作 -> 归为【无助】（例：“遥控器按了半天不出影” -> 无助|轻微）。\n" +
                "6. 【挂念故人】：想念亲属或旧人旧事 -> 归为【思念】。\n" +
                "7. 【明确喜悦】：明确表达自己高兴 -> 归为【开心】。\n" +
                "8. 【客观指令屏蔽】：下达功能指令或客观询问 -> 归为【无情绪|无】。\n" +
                "【判定标准】：\n" +
                "1. 轻微：日常的随口抱怨、短暂的心情波动（例：“今天下雨出不去，真无聊” -> 孤独|轻微）。\n" +
                "2. 严重：情绪极度崩溃、连续表达负能量、明确表达身体痛苦或求救（例：“我胸口疼得喘不上气” -> 身体不适|严重）。\n";

        /***
         * 将老人的用户画像上传给AI
         */
        String healthProfile = "";
        try {
            String sql = "SELECT * FROM user_profiles WHERE uid = ?";
            Map<String, Object> profile = jdbcTemplate.queryForMap(sql, uid);
            healthProfile = String.format("\n\n【🚨 最高优先级系统指令：用户健康档案】\n" +
                            "当前老人年龄：%s岁，性别：%s。\n" +
                            "既往病史：%s\n" +
                            "身体机能：%s\n" +
                            "饮食禁忌与偏好：%s\n" +
                            "要求：你在给出任何建议（特别是饮食、运动、生活作息建议）时，绝对不能违背上述病史和禁忌！必须结合这些情况给出安全、个性化的关怀。",
                    profile.get("age"), profile.get("gender"),
                    profile.get("medical_history"), profile.get("physical_condition"), profile.get("dietary_preference")
            );
        } catch (Exception e) {
            System.out.println("提示：该老人尚未填写健康档案。");
        }
        SystemPrompt += healthProfile;

        // 2. 组装最终发给 AI 的消息列表
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SystemPrompt));
        messages.addAll(conversationHistory);

        // 3. 构建请求体
        Map<String, Object> requestBody = Map.of("model", "moonshot-v1-8k",
                "temperature", 0.7,"messages", messages);

        // 4.发送JSON请求
        RequestBody body = RequestBody.create(JSON.toJSONString(requestBody), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + API_KEY)
                .post(body)
                .build();

        try (Response resp = client.newCall(request).execute())
        {
            if(!resp.isSuccessful()) return "哎呀，不好意思，大福没听清。";

            // 解析结果
            String responseString = resp.body().string();
            JSONObject json = JSON.parseObject(responseString);
            System.out.println("AI返回的内容是：" + responseString);

            return json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
        }
    }
}
