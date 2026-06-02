package com.washy.dify.prompt;

import com.washy.dify.prompt.core.PromptManager;
import com.washy.dify.prompt.util.PromptUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
public class PromptManagerTest {

    @Autowired
    private PromptManager promptManager;

    @Test
    public void testRenderSummaryPrompt() {
        Map<String, Object> context = PromptUtils.buildContext(
                "content", "Java是一种面向对象的编程语言，由Sun Microsystems于1995年推出。",
                "chunkIndex", 1,
                "totalChunks", 1
        );

        String prompt = promptManager.render("document.summary", context);

        assertNotNull(prompt);
        System.out.println("生成的提示词:\n" + prompt);
    }

    @Test
    public void testRenderRAGPrompt() {
        Map<String, Object> context = PromptUtils.buildContext(
                "question", "什么是Java？",
                "contexts", "Java是一种编程语言"
        );

        String prompt = promptManager.render("rag.qa", context);

        assertNotNull(prompt);
        System.out.println("生成的提示词:\n" + prompt);
    }

    @Test
    public void testListAllTemplates() {
        System.out.println("已加载的模板: " + promptManager.getAllTemplateNames());
        assertTrue(promptManager.getAllTemplateNames().size() > 0);
    }
}