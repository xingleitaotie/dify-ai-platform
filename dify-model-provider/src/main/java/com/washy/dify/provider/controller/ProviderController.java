package com.washy.dify.provider.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.washy.dify.common.result.Result;
import com.washy.dify.provider.entity.ProviderEntity;
import com.washy.dify.provider.service.ProviderService;
import com.washy.dify.provider.vo.ProviderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/provider")
@Api(tags = "模型供应商管理")
@Slf4j
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    @GetMapping("/page")
    @ApiOperation("分页查询供应商")
    public Result<Page<ProviderEntity>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(providerService.pageProvider(pageNum, pageSize, keyword));
    }

    @GetMapping("/enabled")
    @ApiOperation("获取所有启用的供应商")
    public Result<List<ProviderEntity>> getEnabled() {
        return Result.success(providerService.getEnabledProviders());
    }

    @GetMapping("/{id}")
    @ApiOperation("获取供应商详情")
    public Result<ProviderVO> getDetail(@PathVariable Long id) {
        return Result.success(providerService.getProviderDetail(id));
    }

    @PostMapping
    @ApiOperation("新增供应商")
    public Result<Boolean> add(@Valid @RequestBody ProviderEntity provider) {
        return Result.success(providerService.addProvider(provider));
    }

    @PutMapping
    @ApiOperation("更新供应商")
    public Result<Boolean> update(@Valid @RequestBody ProviderEntity provider) {
        return Result.success(providerService.updateProvider(provider));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除供应商")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(providerService.deleteProvider(id));
    }
}