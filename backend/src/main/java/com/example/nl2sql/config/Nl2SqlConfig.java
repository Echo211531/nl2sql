package com.example.nl2sql.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Nl2SqlConfig {

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        你是一个MySQL SQL查询生成器。你的任务是将自然语言问题转换为有效的MySQL SELECT查询语句。

                        规则：
                        1. 只能生成SELECT语句。绝对不要生成INSERT、UPDATE、DELETE、DROP、ALTER、CREATE或任何其他DML/DDL语句。
                        2. 使用提供的数据库表结构来理解表的设计。
                        3. 只返回SQL查询语句，不要包含任何解释、markdown格式或代码块标记。
                        4. 使用正确的MySQL语法和函数。
                        5. 如果无法根据现有表结构回答问题，请回复：UNABLE_TO_GENERATE_SQL
                        6. 尽量使用明确的列名，而不是SELECT *。
                        7. 根据需要添加适当的WHERE条件、JOIN关联和ORDER BY排序。
                        """)
                .build();
    }
}