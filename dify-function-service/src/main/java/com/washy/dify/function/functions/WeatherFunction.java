package com.washy.dify.function.functions;

import com.washy.dify.common.annotation.AiFunction;
import org.springframework.stereotype.Component;

@Component
public class WeatherFunction {

    @AiFunction(
            name = "getWeather",
            desc = "获取指定城市的天气",
            params = "{\"city\":\"城市名称，必填\"}"
    )
    public String getWeather(String city) {
        return city + " 当前天气：晴天，25℃，空气质量优";
    }
}