package com.example.nl2sql.controller;

import com.example.nl2sql.model.DatasourceConfig;
import com.example.nl2sql.model.SchemaInfo;
import com.example.nl2sql.service.DatasourceManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasource")
public class DatasourceController {

    private final DatasourceManager datasourceManager;

    public DatasourceController(DatasourceManager datasourceManager) {
        this.datasourceManager = datasourceManager;
    }

    @GetMapping
    public DatasourceConfig getCurrent() {
        return datasourceManager.getCurrentConfig();
    }

    @PostMapping
    public DatasourceResponse save(@RequestBody DatasourceConfig config) {
        boolean testResult = datasourceManager.testConnection(config);
        if (!testResult) {
            return new DatasourceResponse(false, "连接测试失败，请检查配置信息");
        }
        datasourceManager.setDatasource(config);
        return new DatasourceResponse(true, "数据源配置成功");
    }

    @PostMapping("/test")
    public DatasourceResponse test(@RequestBody DatasourceConfig config) {
        boolean result = datasourceManager.testConnection(config);
        return new DatasourceResponse(result, result ? "连接成功" : "连接失败");
    }

    @DeleteMapping
    public DatasourceResponse delete() {
        datasourceManager.closeDatasource();
        return new DatasourceResponse(true, "数据源已清除");
    }

    @GetMapping("/schema")
    public List<SchemaInfo> getSchema() {
        return datasourceManager.getSchemaInfo();
    }

    @PostMapping("/reload")
    public DatasourceResponse reloadSchema() {
        datasourceManager.reloadSchema();
        return new DatasourceResponse(true, "Schema已重新加载");
    }

    public record DatasourceResponse(boolean success, String message) {}
}