package com.washy.dify.rag.util;

import lombok.Getter;

@Getter
public enum FileTypeEnum {
    TXT("txt", "文本文件"),
    PDF("pdf", "PDF文档");

    private final String code;
    private final String desc;

    FileTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}