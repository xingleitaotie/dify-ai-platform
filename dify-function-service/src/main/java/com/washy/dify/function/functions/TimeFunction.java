package com.washy.dify.function.functions;

import cn.hutool.core.date.DateUtil;
import com.washy.dify.common.annotation.AiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 时间工具函数
 * @author Day7
 */
@Slf4j
@Component
public class TimeFunction {

    @AiFunction(
            name = "getCurrentTime",
            desc = "获取当前系统时间，返回格式：yyyy-MM-dd HH:mm:ss"
    )
    public String getCurrentTime(Map<String, Object> params) {
        log.info("执行获取当前时间函数");
        return DateUtil.now();
    }
}