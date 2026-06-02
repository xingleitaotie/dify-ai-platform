package com.washy.dify.rag.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;

/**
 * PDF文档解析器
 */
public class PdfParser {

    /**
     * 解析PDF获取纯文本
     */
    public static String extractText(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {
            
            PDFTextStripper stripper = new PDFTextStripper();
            // 按顺序读取
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }
}