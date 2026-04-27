package com.example.nl2sql.service;

import com.example.nl2sql.model.DatasourceConfig;
import com.example.nl2sql.model.SchemaInfo;
import com.example.nl2sql.security.SqlSecurityValidator;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatasourceManager {

    private static final Logger log = LoggerFactory.getLogger(DatasourceManager.class);

    private volatile DataSource currentDataSource;
    private volatile JdbcTemplate currentJdbcTemplate;
    private volatile DatasourceConfig currentConfig;

    private final VectorStore vectorStore;
    private final SqlSecurityValidator sqlSecurityValidator;

    public DatasourceManager(VectorStore vectorStore, SqlSecurityValidator sqlSecurityValidator) {
        this.vectorStore = vectorStore;
        this.sqlSecurityValidator = sqlSecurityValidator;
    }

    public synchronized void setDatasource(DatasourceConfig config) {
        if (currentDataSource != null) {
            closeDatasource();
        }

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(config.getJdbcUrl());
        dataSource.setUsername(config.username());
        dataSource.setPassword(config.password());
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);

        currentDataSource = dataSource;
        currentJdbcTemplate = new JdbcTemplate(dataSource);
        currentConfig = config;

        reloadSchema();
        log.info("数据源切换成功: {}", config.name());
    }

    public synchronized void closeDatasource() {
        if (currentDataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
        currentDataSource = null;
        currentJdbcTemplate = null;
        currentConfig = null;
    }

    public DatasourceConfig getCurrentConfig() {
        return currentConfig;
    }

    public JdbcTemplate getCurrentJdbcTemplate() {
        return currentJdbcTemplate;
    }

    public boolean testConnection(DatasourceConfig config) {
        try (HikariDataSource testDs = new HikariDataSource()) {
            testDs.setJdbcUrl(config.getJdbcUrl());
            testDs.setUsername(config.username());
            testDs.setPassword(config.password());
            testDs.setDriverClassName("com.mysql.cj.jdbc.Driver");
            testDs.setMaximumPoolSize(1);
            testDs.setConnectionTimeout(5000);

            try (Connection conn = testDs.getConnection()) {
                return conn.isValid(3);
            }
        } catch (SQLException e) {
            log.warn("连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    public List<SchemaInfo> getSchemaInfo() {
        if (currentDataSource == null) {
            return List.of();
        }

        List<SchemaInfo> schemas = new ArrayList<>();
        try (Connection connection = currentDataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();

            ResultSet tables = metaData.getTables(catalog, null, "%", new String[]{"TABLE"});
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                String tableRemarks = tables.getString("REMARKS");

                List<SchemaInfo.ColumnInfo> columns = new ArrayList<>();
                ResultSet colRs = metaData.getColumns(catalog, null, tableName, "%");
                while (colRs.next()) {
                    columns.add(new SchemaInfo.ColumnInfo(
                            colRs.getString("COLUMN_NAME"),
                            colRs.getString("TYPE_NAME"),
                            colRs.getString("REMARKS"),
                            "YES".equals(colRs.getString("IS_NULLABLE"))
                    ));
                }
                colRs.close();

                List<String> primaryKeys = new ArrayList<>();
                ResultSet pkRs = metaData.getPrimaryKeys(catalog, null, tableName);
                while (pkRs.next()) {
                    primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                }
                pkRs.close();

                schemas.add(new SchemaInfo(tableName, tableRemarks, columns.size(), columns, primaryKeys));
            }
            tables.close();
        } catch (SQLException e) {
            log.error("获取Schema失败: {}", e.getMessage());
        }
        return schemas;
    }

    public void reloadSchema() {
        if (currentDataSource == null) {
            return;
        }

        List<Document> documents = loadSchemaDocuments();
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("Schema重新加载完成: {} 张表", documents.size());
        }
    }

    private List<Document> loadSchemaDocuments() {
        List<Document> documents = new ArrayList<>();
        try (Connection connection = currentDataSource.getConnection()) {
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

                ResultSet primaryKeys = metaData.getPrimaryKeys(catalog, null, tableName);
                List<String> pkColumns = new ArrayList<>();
                while (primaryKeys.next()) {
                    pkColumns.add(primaryKeys.getString("COLUMN_NAME"));
                }
                primaryKeys.close();

                if (!pkColumns.isEmpty()) {
                    description.append("主键: ").append(String.join(", ", pkColumns)).append("\n");
                }

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("table_name", tableName);
                metadata.put("column_count", columnNames.size());
                metadata.put("columns", String.join(",", columnNames));

                Document doc = new Document(description.toString(), metadata);
                documents.add(doc);
            }
            tables.close();
        } catch (SQLException e) {
            log.error("加载Schema文档失败: {}", e.getMessage());
        }
        return documents;
    }
}