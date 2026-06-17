package com.washy.dify.function.functions;

import com.washy.dify.common.annotation.AiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 时间工具函数 - 支持多种时间查询
 * @author Washy
 */
@Slf4j
@Component
public class TimeFunction {

    @AiFunction(
            name = "get_current_time",
            desc = "获取当前日期时间信息。可以指定返回格式，支持日期、时间、星期等。",
            params = "{\n" +
                    "  \"format\": {\n" +
                    "    \"type\": \"string\",\n" +
                    "    \"description\": \"日期时间格式，默认为'yyyy-MM-dd HH:mm:ss'。可选值：'date'(yyyy-MM-dd)、'time'(HH:mm:ss)、'datetime'(yyyy-MM-dd HH:mm:ss)、'full'(yyyy年MM月dd日 HH时mm分ss秒)\",\n" +
                    "    \"enum\": [\"date\", \"time\", \"datetime\", \"full\"],\n" +
                    "    \"default\": \"datetime\"\n" +
                    "  },\n" +
                    "  \"timezone\": {\n" +
                    "    \"type\": \"string\",\n" +
                    "    \"description\": \"时区，默认为系统时区\",\n" +
                    "    \"default\": \"system\"\n" +
                    "  }\n" +
                    "}"
    )
    public String getCurrentTime(Map<String, Object> params) {
        log.info("执行时间查询函数，参数：{}", params);

        try {
            String format = params.containsKey("format") ?
                    params.get("format").toString() : "datetime";

            LocalDateTime now = LocalDateTime.now();
            String formattedTime;
            Map<String, Object> result = new HashMap<>();

            switch (format) {
                case "date":
                    formattedTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    break;
                case "time":
                    formattedTime = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    break;
                case "full":
                    formattedTime = now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分ss秒"));
                    break;
                case "datetime":
                default:
                    formattedTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    break;
            }

            // 返回结构化结果
            result.put("success", true);
            result.put("current_time", formattedTime);
            result.put("format", format);
            result.put("timestamp", System.currentTimeMillis());
            result.put("date", now.format(DateTimeFormatter.ISO_LOCAL_DATE));
            result.put("time", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            result.put("day_of_week", getDayOfWeekChinese(now.getDayOfWeek().getValue()));
            result.put("week_of_year", now.get(java.time.temporal.WeekFields.ISO.weekOfYear()));

            log.info("当前时间：{}", formattedTime);
            return new com.alibaba.fastjson2.JSONObject(result).toJSONString();

        } catch (Exception e) {
            log.error("获取时间失败", e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String getDayOfWeekChinese(int dayOfWeek) {
        String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return days[dayOfWeek - 1];
    }
}