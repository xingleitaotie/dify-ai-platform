package com.washy.dify.provider.controller;

import com.washy.dify.common.result.Result;
import com.washy.dify.provider.dto.SystemCapabilitiesDTO;
import com.washy.dify.provider.entity.ModelConfigEntity;
import com.washy.dify.provider.service.SystemConfigService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/provider/capability")
@Api(tags = "系统能力配置")
@Slf4j
@RequiredArgsConstructor
public class SystemCapabilityController {

    private final SystemConfigService capabilityService;

    @GetMapping
    @ApiOperation("获取所有系统能力配置")
    public Result<SystemCapabilitiesDTO> getAll() {
        return Result.success(capabilityService.getAllCapabilities());
    }

    @GetMapping("/{capabilityType}/models")
    @ApiOperation("获取指定能力的可用模型")
    public Result<List<ModelConfigEntity>> getAvailableModels(@PathVariable String capabilityType) {
        return Result.success(capabilityService.getAvailableModels(capabilityType));
    }

    @GetMapping("/types")
    @ApiOperation("获取能力类型列表")
    public Result<List<Map<String, String>>> getTypes() {
        return Result.success(capabilityService.getCapabilityTypes());
    }

    @PutMapping("/{capabilityType}")
    @ApiOperation("更新系统能力配置")
    public Result<Boolean> update(
            @PathVariable String capabilityType,
            @RequestBody Map<String, Long> request) {
        Long modelConfigId = request.get("modelConfigId");
        return Result.success(capabilityService.updateCapability(capabilityType, modelConfigId));
    }

    @PostMapping("/refresh")
    @ApiOperation("刷新缓存")
    public Result<Boolean> refresh() {
        capabilityService.refreshCache();
        return Result.success(true);
    }
}