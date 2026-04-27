package com.example.nl2sql.service;

import com.example.nl2sql.model.DatasourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SchemaManager implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaManager.class);

    private final DatasourceManager datasourceManager;

    public SchemaManager(DatasourceManager datasourceManager) {
        this.datasourceManager = datasourceManager;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("初始化默认数据源...");
        DatasourceConfig defaultConfig = DatasourceConfig.defaultConfig();
        datasourceManager.setDatasource(defaultConfig);
        log.info("默认数据源初始化完成: {}", defaultConfig.name());
    }
}