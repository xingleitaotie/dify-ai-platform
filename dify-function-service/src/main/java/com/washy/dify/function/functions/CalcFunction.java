package com.washy.dify.function.functions;

import com.washy.dify.common.annotation.AiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 计算工具函数
 * @author Day7
 */
@Slf4j
@Component
public class CalcFunction {

    @AiFunction(
            name = "simpleCalc",
            desc = "简单加法计算",
            params = "{\"num1\":\"数字1，必填\",\"num2\":\"数字2，必填\"}"
    )
    public String simpleCalc(Map<String, Object> params) {
        log.info("执行简单加法计算，参数：{}", params);
        try {
            BigDecimal num1 = new BigDecimal(params.get("num1").toString());
            BigDecimal num2 = new BigDecimal(params.get("num2").toString());
            return "计算结果：" + num1.add(num2);
        } catch (Exception e) {
            log.error("计算失败", e);
            return "计算错误：" + e.getMessage();
        }
    }
}