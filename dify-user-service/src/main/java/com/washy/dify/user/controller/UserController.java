package com.washy.dify.user.controller;

import com.washy.dify.common.entity.user.AppKeyDTO;
import com.washy.dify.common.entity.user.UserLoginDTO;
import com.washy.dify.common.entity.user.UserRegisterDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.user.config.JwtUtil;
import com.washy.dify.user.entity.SysApp;
import com.washy.dify.user.service.AppService;
import com.washy.dify.user.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private AppService appService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Boolean> register(@Valid @RequestBody UserRegisterDTO dto) {
        return userService.register(dto);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody UserLoginDTO dto) {
        return userService.login(dto);
    }

    /**
     * 创建应用密钥
     */
    @PostMapping("/app/create")
    public Result<SysApp> createApp(@Valid @RequestBody AppKeyDTO dto,
                                   @RequestHeader("token") String token) {
        Long userId = JwtUtil.getUserId(token);
        return appService.createApp(dto, userId);
    }

    /**
     * 查询应用列表
     */
    @GetMapping("/app/list")
    public Result<List<SysApp>> appList(@RequestHeader("token") String token) {
        Long userId = JwtUtil.getUserId(token);
        return appService.appList(userId);
    }

    /**
     * 删除应用
     */
    @DeleteMapping("/app/delete")
    public Result<Boolean> deleteApp(@RequestParam Long id,
                                     @RequestHeader("token") String token) {
        Long userId = JwtUtil.getUserId(token);
        return appService.deleteApp(id, userId);
    }
}