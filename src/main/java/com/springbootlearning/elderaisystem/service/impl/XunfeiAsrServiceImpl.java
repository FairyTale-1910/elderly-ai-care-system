package com.springbootlearning.elderaisystem.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
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
    private static final String APPID = "你的APPID";
    private static final String API_KEY = "你的API_KEY";
    private static final String API_SECRET = "你的API_SECRET";
    private static final String HOST_URL = "https://iat-api.xfyun.cn/v2/iat";

    public String audioToText(byte[] audioData) {
        StringBuilder resultBuilder = new StringBuilder();
        try {
            String authUrl = getAuthUrl(HOST_URL, API_KEY, API_SECRET);
            String wsUrl = authUrl.replace("https://", "wss://");

            OkHttpClient client = new OkHttpClient.Builder().build();
            Request request = new Request.Builder().url(wsUrl).build();
            CountDownLatch latch = new CountDownLatch(1);

            client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    System.out.println("✅ ASR WebSocket 连接成功！准备发送音频...");
                    new Thread(() -> sendAudioFrames(webSocket, audioData)).start();
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

    private void sendAudioFrames(WebSocket webSocket, byte[] audioData) {
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
                    business.addProperty("language", "zh_cn"); // 中文
                    business.addProperty("domain", "iat");     // 日常交流
                    business.addProperty("accent", "mandarin");// 普通话
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
