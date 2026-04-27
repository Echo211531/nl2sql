package com.example.nl2sql.service;

import com.example.nl2sql.model.DataQueryResponse;
import com.example.nl2sql.security.SqlSecurityValidator;
import com.example.nl2sql.security.SqlSecurityValidator.SqlSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DataQueryService {

    private static final Logger log = LoggerFactory.getLogger(DataQueryService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final SqlSecurityValidator sqlSecurityValidator;

    public DataQueryService(ChatClient chatClient,
                            VectorStore vectorStore,
                            JdbcTemplate jdbcTemplate,
                            SqlSecurityValidator sqlSecurityValidator) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSecurityValidator = sqlSecurityValidator;
    }

    public DataQueryResponse query(String question) {
        if (question == null || question.isBlank()) {
            return DataQueryResponse.error("问题不能为空");
        }

        log.info("收到查询请求: {}", question);

        try {
            // 第一步：从向量数据库召回相关Schema
            String schemaContext = recallSchema(question);
            log.debug("召回的Schema上下文: {}", schemaContext);

            // 第二步：使用大模型生成SQL
            String generatedSql = generateSql(question, schemaContext);
            log.info("生成的SQL: {}", generatedSql);

            if (generatedSql == null || generatedSql.isBlank()
                    || generatedSql.contains("UNABLE_TO_GENERATE_SQL")) {
                return DataQueryResponse.error("无法根据该问题生成SQL语句");
            }

            // 第三步：安全校验
            try {
                sqlSecurityValidator.validate(generatedSql);
            } catch (SqlSecurityException e) {
                log.warn("SQL安全校验未通过: {}", e.getMessage());
                return DataQueryResponse.error("安全校验未通过: " + e.getMessage());
            }

            // 第四步：执行SQL
            List<Map<String, Object>> results = jdbcTemplate.queryForList(generatedSql);

            // 第五步：提取列名
            List<String> columns = new ArrayList<>();
            if (!results.isEmpty()) {
                columns = new ArrayList<>(results.get(0).keySet());
            }

            log.info("查询执行成功，返回 {} 条记录", results.size());
            return DataQueryResponse.success(generatedSql, columns, results);

        } catch (SqlSecurityException e) {
            log.warn("SQL被安全校验器拦截: {}", e.getMessage());
            return DataQueryResponse.error("查询被拦截: " + e.getMessage());
        } catch (Exception e) {
            log.error("查询处理异常: {}", e.getMessage(), e);
            return DataQueryResponse.error("查询执行失败: " + e.getMessage());
        }
    }

    private String recallSchema(String question) {
        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .similarityThreshold(0.5)
                        .build()
        );

        if (relevantDocs == null || relevantDocs.isEmpty()) {
            log.warn("未找到与问题相关的Schema: {}", question);
            return "暂无可用的Schema信息。";
        }

        return relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    private String generateSql(String question, String schemaContext) {
        String userPrompt = String.format("""
                根据以下数据库表结构，为用户的问题生成一条MySQL SELECT查询语句。
                
                数据库表结构:
                %s
                
                用户问题: %s
                
                请仅输出SQL查询语句:""", schemaContext, question);

        String response = chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();

        if (response == null) {
            return null;
        }

        // 清理响应 - 去除可能存在的markdown代码块标记
        String sql = response.trim();
        if (sql.startsWith("```sql")) {
            sql = sql.substring(6);
        } else if (sql.startsWith("```")) {
            sql = sql.substring(3);
        }
        if (sql.endsWith("```")) {
            sql = sql.substring(0, sql.length() - 3);
        }
        return sql.trim();
    }
}
