package com.springbootlearning.elderaisystem.controller;


import com.springbootlearning.elderaisystem.service.impl.XunfeiAsrServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin // 允许跨域，方便前端调用
public class AudioController {
    @Autowired
    private XunfeiAsrServiceImpl xunfeiAsrService;

    @PostMapping("/recognize")
    public ResponseEntity<Map<String, Object>> recognizeAudio(@RequestParam("audio") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 提取出音频字节流
            byte[] audioData = file.getBytes();
            System.out.println("🎤 收到前端录音文件，大小：" + audioData.length + " 字节，准备开始识别...");
            String recognizedText = xunfeiAsrService.audioToText(audioData);

            response.put("status", "success");
            response.put("text", recognizedText);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", "语音解析失败，请检查后端报错");
            return ResponseEntity.status(500).body(response);
        }
    }
}
