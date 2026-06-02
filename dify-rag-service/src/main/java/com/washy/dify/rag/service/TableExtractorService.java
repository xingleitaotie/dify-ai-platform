package com.washy.dify.rag.service;

import com.washy.dify.rag.domain.TableInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class TableExtractorService {
    
    /**
     * 从Word文档中提取所有表格
     */
    public List<TableInfo> extractTables(XWPFDocument document) {
        List<TableInfo> tables = new ArrayList<>();
        
        List<IBodyElement> elements = document.getBodyElements();
        int tableIndex = 0;
        
        for (IBodyElement element : elements) {
            if (element instanceof XWPFTable) {
                XWPFTable table = (XWPFTable) element;
                TableInfo tableInfo = extractTable(table, ++tableIndex);
                tables.add(tableInfo);
                log.debug("表格提取成功: 第{}个表格 ({}行 x {}列)", 
                    tableIndex, tableInfo.getRowCount(), tableInfo.getColumnCount());
            }
        }
        
        log.info("文档中共提取 {} 个表格", tables.size());
        return tables;
    }
    
    /**
     * 提取单个表格
     */
    private TableInfo extractTable(XWPFTable table, int index) {
        List<XWPFTableRow> rows = table.getRows();
        List<String[]> tableRows = new ArrayList<>();
        int maxColumns = 0;
        
        for (int i = 0; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            List<XWPFTableCell> cells = row.getTableCells();
            String[] rowData = new String[cells.size()];
            
            for (int j = 0; j < cells.size(); j++) {
                rowData[j] = cells.get(j).getText().trim();
            }
            
            tableRows.add(rowData);
            maxColumns = Math.max(maxColumns, cells.size());
        }
        
        // 生成表格文本表示
        String tableText = generateTableText(tableRows, maxColumns);
        
        return TableInfo.builder()
            .tableId(UUID.randomUUID().toString())
            .content(tableText)
            .rows(tableRows)
            .rowCount(rows.size())
            .columnCount(maxColumns)
            .hasHeader(rows.size() > 1) // 多于1行则假设有表头
            .caption(String.format("表格%d", index))
            .build();
    }
    
    /**
     * 生成表格的文本表示
     */
    private String generateTableText(List<String[]> rows, int maxColumns) {
        StringBuilder sb = new StringBuilder();
        sb.append("[表格开始]\n");
        
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            sb.append("| ");
            for (int j = 0; j < maxColumns; j++) {
                String cell = j < row.length ? row[j] : "";
                sb.append(cell).append(" | ");
            }
            sb.append("\n");
            
            // 表头后面添加分隔线
            if (i == 0 && rows.size() > 1) {
                sb.append("|");
                for (int j = 0; j < maxColumns; j++) {
                    sb.append(" --- |");
                }
                sb.append("\n");
            }
        }
        
        sb.append("[表格结束]");
        return sb.toString();
    }
}