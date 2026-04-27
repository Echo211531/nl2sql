package com.example.nl2sql.model;

import java.util.List;
import java.util.Map;

public record DataQueryResponse(
        boolean success,
        String sql,
        List<String> columns,
        List<Map<String, Object>> data,
        String errorMessage
) {
    public static DataQueryResponse success(String sql, List<String> columns, List<Map<String, Object>> data) {
        return new DataQueryResponse(true, sql, columns, data, null);
    }

    public static DataQueryResponse error(String message) {
        return new DataQueryResponse(false, null, null, null, message);
    }
}
