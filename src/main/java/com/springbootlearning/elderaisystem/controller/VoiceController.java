package com.springbootlearning.elderaisystem.controller;

import com.springbootlearning.elderaisystem.service.VoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/voice")
@CrossOrigin
public class VoiceController {

    @Autowired
    @Qualifier("xunfeiVoiceService") // 注入你写好的科大讯飞服务
    private VoiceService voiceService;

    /**
     * 将任意文字转换为大福的语音音频 (Base64)
     */
    @GetMapping("/tts")
    public Map<String, String> getTTS(@RequestParam String text) {
        try {
            // 调用科大讯飞接口合成语音
            String base64 = voiceService.textToSpeech(text);
            return Map.of("status", "success", "audioBase64", base64);
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("status", "error");
        }
    }
}
