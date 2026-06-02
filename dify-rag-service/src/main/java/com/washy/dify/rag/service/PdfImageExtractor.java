package com.washy.dify.rag.service;

import com.washy.dify.rag.domain.ImageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class PdfImageExtractor {

    @Autowired
    private ImageExtractorService imageExtractorService;

    /**
     * 提取 PDF 中所有图片
     */
    /**
     * 提取 PDF 中所有图片
     * @return Map<占位符, ImageInfo>
     */
    public Map<String, ImageInfo> extract(PDDocument document, String documentName) {
        Map<String, ImageInfo> imageMap = new LinkedHashMap<>();
        int imageIndex = 0;

        try {
            for (int pageNum = 0; pageNum < document.getNumberOfPages(); pageNum++) {
                PDPage page = document.getPage(pageNum);

                Iterable<COSName> xObjectNames = page.getResources().getXObjectNames();
                if (xObjectNames == null) continue;

                for (COSName name : xObjectNames) {
                    if (page.getResources().isImageXObject(name)) {
                        try {
                            PDImageXObject image = (PDImageXObject) page.getResources().getXObject(name);
                            BufferedImage bufferedImage = image.getImage();

                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            String format = "png";
                            ImageIO.write(bufferedImage, format, baos);
                            byte[] imageData = baos.toByteArray();

                            String originalName = String.format("pdf_page%d_%s.png", pageNum + 1, name.getName());
                            String placeholder = String.format("{{IMAGE_%04d}}", ++imageIndex);

                            // 调用正确的方法名
                            ImageInfo info = imageExtractorService.saveImage(
                                    imageData, originalName, documentName,placeholder);
                            info.setPlaceholder(placeholder);
                            imageMap.put(placeholder, info);

                            log.debug("图片提取成功: {} -> {} (大小: {} bytes)",
                                    originalName, info.getLocalPath(), imageData.length);

                        } catch (Exception e) {
                            log.warn("提取 PDF 图片失败: page={}, name={}", pageNum, name.getName(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("提取 PDF 图片失败", e);
        }

        log.info("PDF 图片提取完成：{} 张", imageMap.size());
        return imageMap;
    }
}