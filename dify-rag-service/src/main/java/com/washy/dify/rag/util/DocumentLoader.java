package com.washy.dify.rag.util;

import cn.hutool.core.util.StrUtil;
import io.micrometer.core.instrument.util.IOUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文档加载器：根据文件类型自动解析
 */
public class DocumentLoader {

    public static String loadText(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (StrUtil.isBlank(fileName)) {
            throw new IllegalArgumentException("文件名为空");
        }

        // 获取后缀
        String suffix = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        if (FileTypeEnum.PDF.getCode().equals(suffix)) {
            return PdfParser.extractText(file);
        } else if (FileTypeEnum.TXT.getCode().equals(suffix)) {
            return loadTxt(file);
        } else {
            throw new IllegalArgumentException("不支持的文件类型：" + suffix);
        }
    }

    /**
     * 读取TXT文件（JDK1.8兼容版）
     */
    private static String loadTxt(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            // JDK1.8 不能用 readAllBytes()，改用 IOUtils 工具类
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        }
    }
}