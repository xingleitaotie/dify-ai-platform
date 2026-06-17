package com.washy.dify.function.functions;

import com.washy.dify.common.annotation.AiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 计算工具函数 - 支持加减乘除运算
 * @author Washy
 */
@Slf4j
@Component
public class CalcFunction {

    @AiFunction(
            name = "simple_calc",
            desc = "执行数学运算。支持加法(add)、减法(subtract)、乘法(multiply)、除法(divide)。" +
                    "返回JSON格式的计算结果。适用于需要进行数值计算的场景。",
            params = "{\n" +
                    "  \"num1\": {\n" +
                    "    \"type\": \"number\",\n" +
                    "    \"description\": \"第一个操作数，例如：10、3.14、-5\",\n" +
                    "    \"required\": true\n" +
                    "  },\n" +
                    "  \"num2\": {\n" +
                    "    \"type\": \"number\",\n" +
                    "    \"description\": \"第二个操作数，例如：20、2.5、8\",\n" +
                    "    \"required\": true\n" +
                    "  },\n" +
                    "  \"operation\": {\n" +
                    "    \"type\": \"string\",\n" +
                    "    \"description\": \"运算类型：add(加)、subtract(减)、multiply(乘)、divide(除)\",\n" +
                    "    \"enum\": [\"add\", \"subtract\", \"multiply\", \"divide\"],\n" +
                    "    \"default\": \"add\"\n" +
                    "  }\n" +
                    "}"
    )
    public String simpleCalc(Map<String, Object> params) {
        log.info("执行计算函数，参数：{}", params);

        try {
            // 参数校验
            if (!params.containsKey("num1") || !params.containsKey("num2")) {
                return "错误：缺少必要参数 num1 或 num2";
            }

            BigDecimal num1 = new BigDecimal(params.get("num1").toString());
            BigDecimal num2 = new BigDecimal(params.get("num2").toString());

            String operation = params.containsKey("operation") ?
                    params.get("operation").toString() : "add";

            BigDecimal result;
            switch (operation) {
                case "add":
                    result = num1.add(num2);
                    break;
                case "subtract":
                    result = num1.subtract(num2);
                    break;
                case "multiply":
                    result = num1.multiply(num2);
                    break;
                case "divide":
                    if (num2.compareTo(BigDecimal.ZERO) == 0) {
                        return "错误：除数不能为零";
                    }
                    result = num1.divide(num2, 2, BigDecimal.ROUND_HALF_UP);
                    break;
                default:
                    return "错误：不支持的运算类型 " + operation;
            }

            // 返回结构化结果，便于大模型理解
            return String.format("{\"success\": true, \"result\": %s, \"operation\": \"%s\", \"expression\": \"%s %s %s = %s\"}",
                    result, operation, num1, getOperator(operation), num2, result);

        } catch (NumberFormatException e) {
            log.error("数字格式错误", e);
            return "{\"success\": false, \"error\": \"数字格式错误，请确保提供有效的数字\"}";
        } catch (Exception e) {
            log.error("计算失败", e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String getOperator(String operation) {
        switch (operation) {
            case "add": return "+";
            case "subtract": return "-";
            case "multiply": return "×";
            case "divide": return "÷";
            default: return "";
        }
    }
}