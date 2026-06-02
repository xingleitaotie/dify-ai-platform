package com.washy.dify.rag.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableInfo {
    private String tableId;              // UUID
    private String content;              // 表格文本内容
    private List<String[]> rows;         // 行数据
    private int rowCount;                // 行数
    private int columnCount;             // 列数
    private boolean hasHeader;           // 是否有表头
    private String caption;              // 表格标题
    private String markdownContent;    // Markdown格式的表格内容
    private String jsonContent;        // JSON格式的表格数据（可选）

    /**
     * 获取格式化的表格内容（用于向量化）
     */
    public String getFormattedContent() {
        if (markdownContent != null && !markdownContent.isEmpty()) {
            return markdownContent;
        }
        return convertToText();
    }

    /**
     * 转换为纯文本格式
     */
    private String convertToText() {
        StringBuilder sb = new StringBuilder();
        if (caption != null && !caption.isEmpty()) {
            sb.append("【").append(caption).append("】\n");
        }

        if (rows == null || rows.isEmpty()) {
            return sb.toString();
        }

        for (String[] row : rows) {
            sb.append("|");
            for (String cell : row) {
                sb.append(" ").append(cell != null ? cell : "").append(" |");
            }
            sb.append("\n");
        }

        sb.append("（共 ").append(rowCount).append(" 行，").append(columnCount).append(" 列）");
        return sb.toString();
    }
}