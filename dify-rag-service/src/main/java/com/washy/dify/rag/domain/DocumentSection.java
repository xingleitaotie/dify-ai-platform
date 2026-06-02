package com.washy.dify.rag.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentSection {
    private String sectionNumber;       // 如"1.1.1"
    private String title;               // 标题文本
    private Integer level;              // 层级(1,2,3...)
    private String fullTitle;           // 完整标题
    private List<Object> contentElements = new ArrayList<>(); // 混合内容(文本、图片、表格)
    private List<DocumentSection> subSections = new ArrayList<>();

    public void addText(String text) {
        contentElements.add(text);
    }

    public void addImage(ImageInfo image) {
        contentElements.add(image);
    }

    public void addTable(String tableJson) {
        contentElements.add(tableJson);
    }
}