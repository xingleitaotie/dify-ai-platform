package com.washy.dify.workflow.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Component
public class WorkFlowTableAutoConfig implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String[] TABLES = {
        "workflow","workflow_node", "workflow_edge","workflow_execution","workflow_node_execution"
    };

    @Override
    public void run(String... args) throws Exception {
        boolean needCreate = false;
        for (String t : TABLES) {
            if (jdbcTemplate.queryForList("SHOW TABLES LIKE '"+t+"'").isEmpty()) {
                needCreate = true; break;
            }
        }
        if (!needCreate) {
            System.out.println("✅ 所有WorkFlow表已存在");
            return;
        }

        Resource resource = new ClassPathResource("mapper/workflow.sql");
        BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().startsWith("--") && !line.trim().isEmpty())
                sb.append(line).append("\n");
        }
        String[] sqls = sb.toString().split(";");
        for (String sql : sqls) {
            if (!sql.trim().isEmpty())
                jdbcTemplate.execute(sql.trim());
        }
        System.out.println("✅ WorkFlow 模块表自动创建完成！");
    }
}