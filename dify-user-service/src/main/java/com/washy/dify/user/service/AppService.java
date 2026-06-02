package com.washy.dify.user.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.washy.dify.common.entity.user.AppKeyDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.user.entity.SysApp;
import com.washy.dify.user.mapper.SysAppMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AppService extends ServiceImpl<SysAppMapper, SysApp> {

    /**
     * 创建应用密钥
     */
    public Result<SysApp> createApp(AppKeyDTO dto, Long userId) {
        SysApp app = new SysApp();
        app.setUserId(userId);
        app.setAppName(dto.getAppName());
        app.setAppKey(UUID.randomUUID().toString().replace("-", ""));
        app.setAppSecret(UUID.randomUUID().toString().replace("-", ""));
        app.setStatus(1);
        save(app);
        return Result.success(app);
    }

    /**
     * 查询用户应用列表
     */
    public Result<List<SysApp>> appList(Long userId) {
        List<SysApp> list = lambdaQuery().eq(SysApp::getUserId, userId).list();
        return Result.success(list);
    }

    /**
     * 删除应用
     */
    public Result<Boolean> deleteApp(Long id, Long userId) {
        boolean remove = lambdaUpdate()
                .eq(SysApp::getId, id)
                .eq(SysApp::getUserId, userId)
                .remove();
        return Result.success(remove);
    }
}