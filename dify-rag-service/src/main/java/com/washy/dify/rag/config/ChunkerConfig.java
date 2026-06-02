package com.washy.dify.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "document")
public class ChunkerConfig {
    
    private ChunkConfig chunk = new ChunkConfig();
    private ImageConfig image = new ImageConfig();
    private TableConfig table = new TableConfig();
    
    @Data
    public static class ChunkConfig {
        private int maxSize;
        private int minSize;
        private int overlap;
        private int maxLevel;
    }
    
    @Data
    public static class ImageConfig {
        private String storagePath;
        private boolean enabled;
        private String maxSize;
    }
    
    @Data
    public static class TableConfig {
        private boolean enabled;
        private int maxRows;
    }
}