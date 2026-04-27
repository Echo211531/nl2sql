package com.example.nl2sql.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SchemaManager implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaManager.class);

    private final DataSource dataSource;
    private final VectorStore vectorStore;

    public SchemaManager(DataSource dataSource, VectorStore vectorStore) {
        this.dataSource = dataSource;
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始初始化数据库Schema...");
        List<Document> documents = loadSchemaDocuments();
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("Schema初始化完成：已将 {} 张表加载到向量数据库", documents.size());
        } else {
            log.warn("数据库中未找到任何表");
        }
    }

    private List<Document> loadSchemaDocuments() throws Exception {
        List<Document> documents = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();

            ResultSet tables = metaData.getTables(catalog, null, "%", new String[]{"TABLE"});

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                String tableRemarks = tables.getString("REMARKS");

                StringBuilder description = new StringBuilder();
                description.append("表: ").append(tableName);
                if (tableRemarks != null && !tableRemarks.isEmpty()) {
                    description.append(" (").append(tableRemarks).append(")");
                }
                description.append("\n字段列表:\n");

                // 加载该表的字段信息
                ResultSet columns = metaData.getColumns(catalog, null, tableName, "%");
                List<String> columnNames = new ArrayList<>();
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    String columnType = columns.getString("TYPE_NAME");
                    String columnRemarks = columns.getString("REMARKS");
                    String nullable = "YES".equals(columns.getString("IS_NULLABLE")) ? "可为空" : "非空";

                    columnNames.add(columnName);
                    description.append("  - ").append(columnName)
                            .append(" (").append(columnType).append(", ").append(nullable).append(")");
                    if (columnRemarks != null && !columnRemarks.isEmpty()) {
                        description.append(" -- ").append(columnRemarks);
                    }
                    description.append("\n");
                }
                columns.close();

                // 加载主键信息
                ResultSet primaryKeys = metaData.getPrimaryKeys(catalog, null, tableName);
                List<String> pkColumns = new ArrayList<>();
                while (primaryKeys.next()) {
                    pkColumns.add(primaryKeys.getString("COLUMN_NAME"));
                }
                primaryKeys.close();

                if (!pkColumns.isEmpty()) {
                    description.append("主键: ").append(String.join(", ", pkColumns)).append("\n");
                }

                // 创建文档并附带元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("table_name", tableName);
                metadata.put("column_count", columnNames.size());
                metadata.put("columns", String.join(",", columnNames));

                Document doc = new Document(description.toString(), metadata);
                documents.add(doc);

                log.debug("已加载表结构: {} (共 {} 个字段)", tableName, columnNames.size());
            }
            tables.close();
        }

        return documents;
    }
}
