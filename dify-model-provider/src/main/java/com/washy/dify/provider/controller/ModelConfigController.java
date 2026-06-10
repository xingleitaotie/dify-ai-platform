package com.washy.dify.provider.controller;

import com.washy.dify.common.result.Result;
import com.washy.dify.provider.entity.ModelConfigEntity;
import com.washy.dify.provider.service.ModelConfigService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/provider/model")
@Api(tags = "模型配置管理")
@Slf4j
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @GetMapping("/provider/{providerId}")
    @ApiOperation("获取供应商下的所有模型")
    public Result<List<ModelConfigEntity>> listByProvider(@PathVariable Long providerId) {
        return Result.success(modelConfigService.listByProvider(providerId));
    }

    @GetMapping("/capability/{capabilityType}")
    @ApiOperation("获取指定能力的可用模型")
    public Result<List<ModelConfigEntity>> listByCapability(@PathVariable String capabilityType) {
        return Result.success(modelConfigService.listByCapability(capabilityType));
    }

    @PostMapping
    @ApiOperation("添加模型配置")
    public Result<Boolean> add(@Valid @RequestBody ModelConfigEntity modelConfig) {
        return Result.success(modelConfigService.add(modelConfig));
    }

    @PutMapping
    @ApiOperation("更新模型配置")
    public Result<Boolean> update(@Valid @RequestBody ModelConfigEntity modelConfig) {
        return Result.success(modelConfigService.update(modelConfig));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除模型配置")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(modelConfigService.delete(id));
    }

    @PostMapping("/test")
    @ApiOperation("测试模型连接")
    public Result<Boolean> test(@RequestBody Map<String, String> params) {
        boolean success = modelConfigService.test(params);
        return success ? Result.success(true) : Result.error("连接测试失败");
    }
}