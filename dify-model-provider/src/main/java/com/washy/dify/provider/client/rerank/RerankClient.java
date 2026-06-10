package com.washy.dify.provider.client.rerank;

import java.util.List;

/**
 * 重排序客户端接口
 */
public interface RerankClient {
    
    /**
     * 重排序结果
     */
    class RerankResult {
        private int index;
        private String document;
        private double score;
        
        public RerankResult(int index, String document, double score) {
            this.index = index;
            this.document = document;
            this.score = score;
        }
        
        public int getIndex() { return index; }
        public String getDocument() { return document; }
        public double getScore() { return score; }
    }
    
    /**
     * 重排序
     */
    List<RerankResult> rerank(String query, List<String> documents, int topK);
    
    /**
     * 测试连接
     */
    boolean testConnection();
}