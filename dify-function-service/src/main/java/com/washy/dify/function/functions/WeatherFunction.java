package com.washy.dify.function.functions;

import com.alibaba.fastjson2.JSONObject;
import com.washy.dify.common.annotation.AiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 天气查询工具函数 - 优化版
 * @author Day7
 */
@Slf4j
@Component
public class WeatherFunction {

    @AiFunction(
            name = "get_weather",
            desc = "查询指定城市的天气信息，包括温度、天气状况、空气质量、湿度、风力等。支持查询指定日期或默认今天。",
            params = "{\n" +
                    "  \"city\": {\n" +
                    "    \"type\": \"string\",\n" +
                    "    \"description\": \"城市名称，例如：北京、上海、深圳\",\n" +
                    "    \"required\": true\n" +
                    "  },\n" +
                    "  \"date\": {\n" +
                    "    \"type\": \"string\",\n" +
                    "    \"description\": \"查询日期，格式为：yyyy年M月d日 或 yyyy-MM-dd。如果不提供则查询今天\",\n" +
                    "    \"pattern\": \"^\\\\d{4}年\\\\d{1,2}月\\\\d{1,2}日$|^\\\\d{4}-\\\\d{2}-\\\\d{2}$\"\n" +
                    "  },\n" +
                    "  \"unit\": {\n" +
                    "    \"type\": \"string\",\n" +
                    "    \"description\": \"温度单位，celsius(摄氏度) 或 fahrenheit(华氏度)\",\n" +
                    "    \"enum\": [\"celsius\", \"fahrenheit\"],\n" +
                    "    \"default\": \"celsius\"\n" +
                    "  }\n" +
                    "}"
    )
    public String getWeather(Map<String, Object> params) {
        log.info("执行天气查询函数，参数：{}", params);

        try {
            // 参数校验
            if (!params.containsKey("city") || params.get("city") == null) {
                return "{\"success\": false, \"error\": \"城市名称不能为空\", \"weather_info\": null}";
            }

            String city = params.get("city").toString();
            String date = params.containsKey("date") ? params.get("date").toString() : "";
            String unit = params.containsKey("unit") ? params.get("unit").toString() : "celsius";

            // 处理日期
            if (date == null || date.isEmpty()) {
                date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
            } else if (date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                // 转换格式
                LocalDate localDate = LocalDate.parse(date);
                date = localDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
            }

            // 调用真实的天气API（这里使用模拟数据）
            Map<String, Object> weatherData = fetchWeatherData(city, date);

            // 温度单位转换
            int temperature = (int) weatherData.get("temperature");
            if ("fahrenheit".equals(unit)) {
                temperature = celsiusToFahrenheit(temperature);
            }

            // 构建结构化返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("city", city);
            result.put("date", date);
            result.put("weather", weatherData.get("weather"));
            result.put("temperature", temperature);
            result.put("temperature_unit", "celsius".equals(unit) ? "℃" : "℉");
            result.put("air_quality", weatherData.get("airQuality"));
            result.put("air_quality_index", weatherData.get("airQualityIndex"));
            result.put("humidity", weatherData.get("humidity"));
            result.put("wind", weatherData.get("wind"));
            result.put("suggestion", getWeatherSuggestion((String) weatherData.get("weather"), temperature));

            String jsonResult = new JSONObject(result).toJSONString();
            log.info("天气查询完成：{} - {}", city, weatherData.get("weather"));
            return jsonResult;

        } catch (Exception e) {
            log.error("天气查询失败", e);
            return "{\"success\": false, \"error\": \"查询天气情况失败：" + e.getMessage() + "\", \"weather_info\": null}";
        }
    }

    private Map<String, Object> fetchWeatherData(String city, String date) {
        // 模拟天气数据（实际应用中应调用真实API）
        Map<String, Object> data = new HashMap<>();
        data.put("city", city);
        data.put("date", date);
        data.put("weather", "晴");
        data.put("temperature", 25);
        data.put("airQuality", "优");
        data.put("airQualityIndex", 45);
        data.put("humidity", 60);
        data.put("wind", "东北风 2级");

        // 可以根据城市和日期做些差异化
        if ("上海".equals(city)) {
            data.put("weather", "多云");
            data.put("temperature", 23);
            data.put("humidity", 75);
        } else if ("深圳".equals(city)) {
            data.put("weather", "阵雨");
            data.put("temperature", 28);
            data.put("humidity", 85);
        }

        return data;
    }

    private int celsiusToFahrenheit(int celsius) {
        return (int) (celsius * 9 / 5.0 + 32);
    }

    private String getWeatherSuggestion(String weather, int temperature) {
        if (temperature > 30) {
            return "天气炎热，注意防暑降温";
        } else if (temperature < 10) {
            return "天气寒冷，注意保暖";
        } else if (weather.contains("雨")) {
            return "有雨，出门请带伞";
        } else if (weather.contains("雪")) {
            return "有雪，注意出行安全";
        }
        return "天气不错，适宜出行";
    }
}