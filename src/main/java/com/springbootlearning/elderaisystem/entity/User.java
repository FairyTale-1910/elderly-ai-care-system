package com.springbootlearning.elderaisystem.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Long uid;
    private String username;
    private String role;
    private LocalDateTime createdAt;
}
