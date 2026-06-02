package com.washy.dify.rag.service;

import com.washy.dify.rag.config.ChunkerConfig;
import com.washy.dify.rag.domain.ImageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ImageExtractorService {
    
    @Autowired
    private ChunkerConfig config;
    
    // 文档图片映射：文档名 -> (占位符 -> ImageInfo)
    private final Map<String, Map<String, ImageInfo>> documentImages = new ConcurrentHashMap<>();
    
    /**
     * 从Word文档中提取所有图片
     * @return 占位符到图片信息的映射
     */
    public Map<String, ImageInfo> extractImages(XWPFDocument document, String documentName) {
        Map<String, ImageInfo> imageMap = new LinkedHashMap<>();
        
        try {
            List<XWPFPictureData> pictures = document.getAllPictures();
            log.info("文档[{}]中共发现 {} 张图片", documentName, pictures.size());
            
            for (int i = 0; i < pictures.size(); i++) {
                XWPFPictureData picture = pictures.get(i);
                try {
                    byte[] imageData = picture.getData();
                    String originalName = picture.getFileName();
                    
                    // 生成唯一占位符
                    String placeholder = String.format("{{IMAGE_%04d}}", i + 1);
                    
                    // 保存图片并创建信息对象
                    ImageInfo imageInfo = saveImage(imageData, originalName, documentName, placeholder);
                    imageMap.put(placeholder, imageInfo);
                    
                    log.debug("图片提取成功: {} -> {} (大小: {} bytes)", 
                        originalName, imageInfo.getLocalPath(), imageData.length);
                        
                } catch (Exception e) {
                    log.error("提取图片失败: {}", picture.getFileName(), e);
                }
            }
            
            // 缓存图片映射
            documentImages.put(documentName, imageMap);
            
        } catch (Exception e) {
            log.error("提取文档图片失败: {}", documentName, e);
        }
        
        log.info("文档[{}]图片提取完成，共 {} 张", documentName, imageMap.size());
        return imageMap;
    }
    
    /**
     * 保存图片到本地存储 - 修复EMF格式支持
     */
    ImageInfo saveImage(byte[] imageData, String originalName,
                        String documentName, String placeholder) throws IOException {
        String imageId = UUID.randomUUID().toString();
        String format = detectImageFormat(originalName, imageData);
        String fileName = imageId + "." + format;

        Path docDir = Paths.get(config.getImage().getStoragePath(),
                sanitizeDocumentName(documentName));
        Files.createDirectories(docDir);

        Path imagePath = docDir.resolve(fileName);
        Files.write(imagePath, imageData);

        // 对于EMF/WMF等矢量格式，跳过尺寸检测
        int width = 0, height = 0;
        if (!format.equals("emf") && !format.equals("wmf")) {
            try {
                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
                if (bufferedImage != null) {
                    width = bufferedImage.getWidth();
                    height = bufferedImage.getHeight();
                }
            } catch (Exception e) {
                log.debug("无法获取图片尺寸: {}", originalName);
            }
        }

        return ImageInfo.builder()
                .imageId(imageId)
                .localPath(imagePath.toString())
                .originalName(originalName)
                .format(format)
                .size(imageData.length)
                .width(width)
                .height(height)
                .placeholder(placeholder)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 检测图片格式 - 增加EMF/WMF支持
     */
    private String detectImageFormat(String fileName, byte[] data) {
        if (fileName != null && fileName.contains(".")) {
            String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            if (ext.matches("png|jpg|jpeg|gif|bmp|webp|emf|wmf|tiff|tif|svg")) {
                if (ext.equals("jpg")) return "jpeg";
                return ext;
            }
        }
        if (data.length >= 4) {
            if (data[0] == (byte)0xFF && data[1] == (byte)0xD8) return "jpeg";
            if (data[0] == (byte)0x89 && data[1] == (byte)0x50) return "png";
            if (data[0] == (byte)0x47 && data[1] == (byte)0x49) return "gif";
            if (data[0] == (byte)0x42 && data[1] == (byte)0x4D) return "bmp";
            // EMF: 0x01 0x00 0x00 0x00
            if (data[0] == 0x01 && data[1] == 0x00) return "emf";
        }
        return "bin";
    }
    
    /**
     * 替换文本中的图片占位符为实际路径
     */
    public String replacePlaceholders(String text, String documentName) {
        Map<String, ImageInfo> imageMap = documentImages.get(documentName);
        if (imageMap == null || imageMap.isEmpty() || text == null) {
            return text;
        }
        
        String result = text;
        for (Map.Entry<String, ImageInfo> entry : imageMap.entrySet()) {
            String placeholder = entry.getKey();
            ImageInfo imageInfo = entry.getValue();
            // 替换为图片路径标记
            String replacement = String.format("[图片: %s]", imageInfo.getLocalPath());
            result = result.replace(placeholder, replacement);
        }
        
        return result;
    }
    
    /**
     * 上传图片到对象存储(预留方法)
     */
    public String uploadToObjectStorage(String localPath) {
        // TODO: 实现对象存储上传
        log.info("对象存储上传接口预留: {}", localPath);
        return localPath;
    }
    
    /**
     * 清理文档名中的特殊字符
     */
    private String sanitizeDocumentName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5._-]", "_");
    }
    
    /**
     * 清理文档的图片缓存
     */
    public void clearDocumentImages(String documentName) {
        documentImages.remove(documentName);
    }
    
    /**
     * 获取文档的图片映射
     */
    public Map<String, ImageInfo> getDocumentImages(String documentName) {
        return documentImages.getOrDefault(documentName, Collections.emptyMap());
    }
}