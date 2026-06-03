package com.washy.dify.function.functions;

import com.alibaba.fastjson2.JSONObject;
import com.washy.dify.common.annotation.AiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
public class WeatherFunction {

    @AiFunction(
            name = "getWeather",
            desc = "获取指定城市的天气",
            params = "{\"city\":\"城市名称，必填\",\"date\":\"查询日期，选填，格式：yyyy年M月d日\"}"
    )
    public String getWeather(Map<String, Object> params) {
        log.info("执行天气查询函数，参数：{}", params);

        JSONObject result = new JSONObject();

        try {
            String city = params.get("city").toString();
            String date = params.get("date") == null ? "" : params.get("date").toString();

            if (date.isEmpty()) {
                date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
            }

            // 构建返回的JSON数据
            result.put("code", 200);
            result.put("message", "success");
            String finalDate = date;
            result.put("data", new JSONObject() {{
                put("city", city);
                put("date", finalDate);
                put("weather", "晴天");
                put("temperature", 25);
                put("temperatureUnit", "℃");
                put("airQuality", "优");
                put("airQualityIndex", 45);  // 示例AQI数值
                put("humidity", 60);          // 示例湿度
                put("wind", "东北风 2级");    // 示例风力
            }});

        } catch (Exception e) {
            log.error("查询失败", e);
            result.put("code", 500);
            result.put("message", "查询天气情况失败：" + e.getMessage());
            result.put("data", null);
        }

        return result.toJSONString();
    }
}