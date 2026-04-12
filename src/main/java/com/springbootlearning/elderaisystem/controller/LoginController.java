package com.springbootlearning.elderaisystem.controller;

import com.springbootlearning.elderaisystem.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class LoginController {
    @Autowired // 自动装配：Spring 看到这个，会自动把配置好的数据库连接池塞进下面的变量里
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/login") // 监听动作：规定这个接口只接收 POST 请求，路径是 /api/login
    public Object login(@RequestBody User LoginRequest)
    {
        String sql = "SELECT * FROM users WHERE username = ? AND ROLE = ?";
        List<User> users = jdbcTemplate.query(
                sql,
                new BeanPropertyRowMapper<>(User.class),
                LoginRequest.getUsername(),
                LoginRequest.getRole()
        );

        if(users.isEmpty())
        {
            return "登录失败：用户不存在或身份错误！";
        }
        else
        {
            return users.get(0);
        }
    }

}
