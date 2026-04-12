package com.springbootlearning.elderaisystem.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
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
    // 目前使用的是Kimi的AI大模型
    private final String API_KEY = "sk-HRbiG8dN9ppcDGePiD1XkbPpOCCftYagVNHmf4X3fiCLVAZ2";
    // Kimi的API地址
    private final String API_URL = "https://api.moonshot.cn/v1/chat/completions";
    // 创建一个OkHttpClient对象，用于发送HTTP请求
    private final OkHttpClient client = new OkHttpClient();

    public String getAIResponse(List<Map<String, String>> conversationHistory) throws IOException {
        String currentTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        // 1. 定义人设
        String SystemPrompt = "你叫“豆包”，是专门陪伴爷爷奶奶的AI小助手。你的性格像他们孝顺、贴心的孙女/孙子。" +
                "请用极度温暖、亲切、有耐心的中文和老人聊天。多用'您'，多关心他们的感受。" +
                "清晰易懂；态度要温和，不急躁，不敷衍；多倾听，多共情。切忌使用网络用语或哄小孩的语气。" +
                "为了方便老人阅读，每次回复请尽量控制在 50 个字以内。" +

                "【⚠️ 重要安全红线（你必须绝对遵守，优先级最高）：】\n" +
                "1. 医疗红线：你绝对没有行医资格。如果老人提到任何身体不适、疼痛、疾病或询问用药，严禁推荐任何具体的药物名称或治疗方案！你必须温柔地安抚老人，并强烈建议他们去医院就诊或联系子女。\n" +
                "2. 事实红线：对于你不确定的事实，请直接用家常话回答“爷爷/奶奶，这个我还真不太清楚呢”，绝不允许编造虚假信息。\n" +

                "【当前系统时间】：" + currentTime + "\n\n" +
                "【特殊指令：提醒识别】\n" +
                "如果老人的话中包含设置提醒的意图，请在回复的最末尾加上以下格式的代码：\n" +
                "[REMINDER_TASK]内容|时间|频率[/REMINDER_TASK]\n" +
                "- 频率规则：ONCE (一次性), DAILY (每天), WEEKLY (每周)\n" +
                "- 时间规则：必须自己根据【当前系统时间】计算出准确的日期和时间，格式为 yyyy-MM-dd HH:mm:00\n" +
                "⚠️ 极度重要：如果老人说的是“10分钟后”、“半小时后”等相对时间，你必须根据上方提供的【当前系统时间】自己计算出准确的时间！绝对不能输出“10分钟后”这种中文文字！\n" +
                "【严格示例学习】（你必须模仿以下格式计算时间）：\n" +
                "系统时间 20:00，老人说“7分钟后叫我吃药” -> [REMINDER_TASK]吃药|20:07|ONCE[/REMINDER_TASK]\n" +
                "系统时间 14:00，老人说“每天下午3点喝水” -> [REMINDER_TASK]喝水|15:00|DAILY[/REMINDER_TASK]\n" +
                "系统时间 23:10，老人说“半小时后提醒我睡觉” -> [REMINDER_TASK]睡觉|23:40|ONCE[/REMINDER_TASK]\n";


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
            if(!resp.isSuccessful()) return "哎呀，不好意思，豆包没听清。";

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
