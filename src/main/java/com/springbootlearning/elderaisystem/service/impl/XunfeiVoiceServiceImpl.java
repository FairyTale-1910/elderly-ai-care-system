package com.springbootlearning.elderaisystem.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.springbootlearning.elderaisystem.service.VoiceService;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Service("xunfeiVoiceService")
public class XunfeiVoiceServiceImpl implements VoiceService {
    private static final String APPID = "你的APPID";
    private static final String API_KEY = "你的API_KEY";
    private static final String API_SECRET = "你的API_SECRET";
    private static final String HOST_URL = "https://cbm01.cn-huabei-1.xf-yun.com/v1/private/mcd9m97e6\n";

    @Override
    public String textToSpeech(String text) {
//        // 🛑 开发阶段屏蔽真实调用，节约成本
//        System.out.println("【模拟语音合成输出】: " + text);
//        return null; // 返回 null，前端就不会播放任何声音，但流程依然能顺利走完
        try {
            String authUrl = getAuthUrl(HOST_URL, API_KEY, API_SECRET);
            String wsUrl = authUrl.replace("https://", "wss://");

            // 准备网络连接
            OkHttpClient client = new OkHttpClient.Builder().build();
            Request request = new Request.Builder().url(wsUrl).build();

            // 收集音频数据
            ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
            CountDownLatch latch = new CountDownLatch(1);

            client.newWebSocket(request, new WebSocketListener() {

                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    sendTextToXunfei(webSocket, text);
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    try {
                        Gson gson = new Gson();
                        JsonObject responseObj = gson.fromJson(text, JsonObject.class);

                        if (responseObj.has("header")) {
                            JsonObject header = responseObj.getAsJsonObject("header");
                            if (header.get("code").getAsInt() != 0) {
                                System.err.println("❌ 科大讯飞报错：" + header.get("message").getAsString());
                                latch.countDown();
                                return;
                            }
                        }

                        if (responseObj.has("payload")) {
                            JsonObject payload = responseObj.getAsJsonObject("payload");
                            if (payload.has("audio")) {
                                JsonObject audio = payload.getAsJsonObject("audio");

                                String audioBase64 = audio.get("audio").getAsString();
                                audioBuffer.write(Base64.getDecoder().decode(audioBase64));

                                if (audio.get("status").getAsInt() == 2) {
                                    webSocket.close(1000, "Done");
                                    latch.countDown();
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        latch.countDown();
                    }
                }
                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    System.err.println("讯飞连接失败：" + t.getMessage());
                    latch.countDown();
                }
            });
            latch.await(10, TimeUnit.SECONDS);

            return Base64.getEncoder().encodeToString(audioBuffer.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void sendTextToXunfei(WebSocket webSocket, String text) {
        JsonObject frame = new JsonObject();

        // header 部分
        JsonObject header = new JsonObject();
        header.addProperty("app_id", APPID);
        header.addProperty("status", 2);
        frame.add("header", header);

        // parameter 部分
        JsonObject parameter = new JsonObject();
        JsonObject oral = new JsonObject();
        oral.addProperty("oral_level", "mid");
        parameter.add("oral", oral);

        JsonObject audio = new JsonObject();
        audio.addProperty("encoding", "lame"); // mp3 格式
        audio.addProperty("sample_rate", 24000);
        audio.addProperty("channels", 1);
        audio.addProperty("bit_depth", 16);
        audio.addProperty("frame_size", 0);

        JsonObject tts = new JsonObject();
        tts.addProperty("vcn", "x6_lingfeiyi_pro");
        tts.addProperty("speed", 50);
        tts.addProperty("volume", 50);
        tts.addProperty("pitch", 50);
        tts.addProperty("bgs", 0);
        tts.addProperty("reg", 0);
        tts.addProperty("rdn", 0);
        tts.addProperty("rhy", 0);
        tts.add("audio", audio);
        parameter.add("tts", tts);
        frame.add("parameter", parameter);

        // payload 部分
        JsonObject payload = new JsonObject();
        JsonObject textObj = new JsonObject();
        textObj.addProperty("encoding", "utf8");
        textObj.addProperty("compress", "raw");
        textObj.addProperty("format", "plain");
        textObj.addProperty("status", 2);
        textObj.addProperty("seq", 0);
        textObj.addProperty("text", Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
        payload.add("text", textObj);
        frame.add("payload", payload);

        webSocket.send(frame.toString());
    }

    private String getAuthUrl(String hostUrl, String apiKey, String apiSecret) throws  Exception{
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
}
