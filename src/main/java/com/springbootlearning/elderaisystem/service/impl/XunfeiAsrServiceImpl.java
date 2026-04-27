package com.springbootlearning.elderaisystem.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;

@Service("xunfeiAsrService")
public class XunfeiAsrServiceImpl {
    @Value("${xunfei.asr.app-id}")
    private String APPID;

    @Value("${xunfei.asr.api-key}")
    private String API_KEY;

    @Value("${xunfei.asr.api-secret}")
    private String API_SECRET;

    @Value("${xunfei.asr.host-url}")
    private String HOST_URL;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String audioToText(byte[] audioData, Long uid) {
        StringBuilder resultBuilder = new StringBuilder();
        try {
            String authUrl = getAuthUrl(HOST_URL, API_KEY, API_SECRET);
            String wsUrl = authUrl.replace("https://", "wss://");

            OkHttpClient client = new OkHttpClient.Builder().build();
            Request request = new Request.Builder().url(wsUrl).build();
            CountDownLatch latch = new CountDownLatch(1);

            // 识别用户所用的语言
            String dialectStr = "";
            try {
                String sql = "SELECT dialect FROM user_profiles WHERE uid = ?";
                dialectStr = jdbcTemplate.queryForObject(sql, String.class, uid);
                if(dialectStr == null) dialectStr = "普通话";
            } catch (Exception e) {
                System.out.println("未找到用户方言设置，默认使用普通话");
                dialectStr = "普通话";
            }

            String language = "zh_cn"; // 默认大语种是中文
            String accent = "mandarin"; // 默认口音是普通话

            // 根据讯飞所激活的语种添加相应的参数
            if (dialectStr.contains("粤") || dialectStr.contains("广东")) {
                accent = "cn_cantonese";
            } else if (dialectStr.contains("上海") || dialectStr.contains("沪")) {
                accent = "shanghainese";
            } else if (dialectStr.contains("河南")) {
                accent = "henanese";
            } else if (dialectStr.contains("英语") || dialectStr.toLowerCase().contains("english")) {
                language = "en_us"; // 如果选了英语，大语种也要换
                accent = "mandarin";
            }
            final String finalLang = language;
            final String finalAccent = accent;

            client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    System.out.println("✅ ASR WebSocket 连接成功！准备发送音频...");
                    new Thread(() -> sendAudioFrames(webSocket, audioData, finalLang, finalAccent)).start();
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    try {
                        Gson gson = new Gson();
                        JsonObject responseObj = gson.fromJson(text, JsonObject.class);
                        if (responseObj.has("code") && responseObj.get("code").getAsInt() != 0)
                        {
                            System.err.println("❌ ASR 报错：" + responseObj.get("message").getAsString());
                            latch.countDown();
                            return;
                        }

                        if(responseObj.has("data"))
                        {
                            JsonObject data = responseObj.getAsJsonObject("data");
                            if(data.has("result"))
                            {
                                JsonObject result = data.getAsJsonObject("result");
                                // 遍历 ws (words) 数组
                                if(result.has("ws"))
                                {
                                    result.getAsJsonArray("ws").forEach(wsElement -> {
                                        wsElement.getAsJsonObject().getAsJsonArray("cw").forEach(cwElement -> {
                                            String word = cwElement.getAsJsonObject().get("w").getAsString();
                                            resultBuilder.append(word);
                                        });
                                    });
                                }
                            }

                            if (data.has("status") && data.get("status").getAsInt() == 2)
                            {
                                System.out.println("🎙️ ASR 识别完毕，老人说的话是：" + resultBuilder.toString());
                                webSocket.close(1000, "Done");
                                latch.countDown();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("❌ ASR JSON解析异常");
                        latch.countDown(); // 发生异常也必须解锁保平安
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    System.out.println("❌ ASR WebSocket 连接失败！");
                    latch.countDown();
                }
            });

            // 最长等待 10 秒识别结果
            latch.await(10, TimeUnit.SECONDS);
            return resultBuilder.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "抱歉，系统开小差了，能再说一遍吗？";
        }
    }

    public String getAuthUrl(String hostUrl, String apiKey, String apiSecret) throws Exception {
        URL url = new URL(hostUrl);
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());
        String preStr = "host: " + url.getHost() + "\n" + "date: " + date + "\n" + "GET " + url.getPath() + " HTTP/1.1";
        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(preStr.getBytes(StandardCharsets.UTF_8));
        String sha = Base64.getEncoder().encodeToString(hexDigits);
        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, "hmac-sha256", "host date request-line", sha);
        String authBase = Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8));
        return String.format("%s?authorization=%s&date=%s&host=%s", hostUrl, authBase, date.replace(" ", "%20"), url.getHost());
    }

    private void sendAudioFrames(WebSocket webSocket, byte[] audioData, String language, String accent) {
        int frameSize = 1280; // 每刀切 1280 字节（代表 40 毫秒）
        int len = audioData.length;
        int index = 0;
        try {
            while(index < len)
            {
                int readlen = min(frameSize, len - index);
                byte[] frame = new byte[readlen];
                System.arraycopy(audioData, index, frame, 0, readlen);

                int status = 1;
                if(index == 0) status = 0;
                if(index + readlen >= len) {
                    status = (status == 0) ? 0 : 2; // 如果音频极短，保持0，下面会补发2
                }

                JsonObject requestJson = new JsonObject();
                if(status == 0)
                {
                    JsonObject common = new JsonObject();
                    common.addProperty("app_id", APPID);
                    requestJson.add("common", common);

                    JsonObject business = new JsonObject();
                    String voiceType = "";
                    business.addProperty("language", language); // 根据用户语言传入
                    business.addProperty("domain", "iat");     // 日常交流
                    business.addProperty("accent", accent);// 普通话
                    business.addProperty("eos", 2000); // 停顿 2000 毫秒算说完
                    requestJson.add("business", business);
                }

                JsonObject data = new JsonObject();
                data.addProperty("status", status);
                data.addProperty("format", "audio/L16;rate=16000"); // 告诉讯飞音频格式
                data.addProperty("encoding", "raw");
                data.addProperty("audio", Base64.getEncoder().encodeToString(frame));
                requestJson.add("data", data);

                webSocket.send(requestJson.toString());
                index += readlen;
                Thread.sleep(40);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
