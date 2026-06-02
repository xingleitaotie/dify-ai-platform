package com.washy.dify.user.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.washy.dify.common.entity.user.UserLoginDTO;
import com.washy.dify.common.entity.user.UserRegisterDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.common.result.ResultCode;
import com.washy.dify.user.config.JwtUtil;
import com.washy.dify.user.entity.SysUser;
import com.washy.dify.user.mapper.SysUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class UserService extends ServiceImpl<SysUserMapper, SysUser> {

    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * 用户注册
     */
    public Result<Boolean> register(UserRegisterDTO dto) {
        // 检查用户名是否存在
        if (lambdaQuery().eq(SysUser::getUsername, dto.getUsername()).exists()) {
            // 👇 修复：使用枚举
            return Result.failed(ResultCode.USER_EXIST);
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setStatus(1);

        boolean save = save(user);
        return Result.success(save);
    }

    /**
     * 用户登录
     */
    public Result<String> login(UserLoginDTO dto) {
        SysUser user = lambdaQuery().eq(SysUser::getUsername, dto.getUsername()).one();
        if (user == null) {
            // 👇 修复
            return Result.failed(ResultCode.USER_NOT_EXIST);
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            // 👇 修复
            return Result.failed(ResultCode.UNAUTHORIZED);
        }

        if (user.getStatus() == 0) {
            // 👇 修复
            return Result.failed(ResultCode.FORBIDDEN);
        }

        // 生成JWT
        String token = JwtUtil.generateToken(user.getId(), user.getUsername());
        return Result.success(token);
    }
}