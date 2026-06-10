package com.washy.dify.feign.client;


import com.washy.dify.common.entity.prompt.PromptTemplateVO;
import com.washy.dify.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "dify-prompt-engine")
public interface PromptFeignClient {
    @GetMapping("/api/prompt/templates")
    Result<List<PromptTemplateVO>> listTemplates();

    /**
     * 动态路由：根据用户查询智能选择提示词模板
     */
    @PostMapping("/api/prompt/route")
    public Result<PromptTemplateVO> route(@RequestBody Map<String, String> request);

    /**
     * 根据类型获取模板
     */
    @GetMapping("/api/prompt/templates/by-type")
    Result<List<PromptTemplateVO>> getTemplatesByType(@RequestParam String type);

    /**
     * 获取模板
     */
    @GetMapping("/api/prompt/template/{id}")
    Result<PromptTemplateVO> getTemplate(@PathVariable String id);

}
