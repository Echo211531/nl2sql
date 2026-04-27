package com.example.nl2sql.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class SqlSecurityValidator {

    private static final Pattern BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT_PATTERN = Pattern.compile("--[^\r\n]*");
    private static final Pattern DANGEROUS_KEYWORDS_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|REPLACE|MERGE|RENAME|GRANT|REVOKE|EXEC|EXECUTE|CALL)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DANGEROUS_FUNCTIONS_PATTERN = Pattern.compile(
            "\\b(LOAD_FILE|BENCHMARK|SLEEP)\\b|INTO\\s+OUTFILE|INTO\\s+DUMPFILE|LOAD\\s+DATA",
            Pattern.CASE_INSENSITIVE
    );

    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlSecurityException("SQL语句为空");
        }

        String cleaned = stripComments(sql).trim();

        if (cleaned.isEmpty()) {
            throw new SqlSecurityException("去除注释后SQL语句为空");
        }

        // 检查多语句（防止堆叠攻击）
        String[] statements = cleaned.split(";");
        long nonEmptyCount = 0;
        for (String stmt : statements) {
            if (!stmt.trim().isEmpty()) {
                nonEmptyCount++;
            }
        }
        if (nonEmptyCount > 1) {
            throw new SqlSecurityException("不允许执行多条SQL语句");
        }

        // 首个关键词必须是 SELECT 或 WITH
        String upperCleaned = cleaned.toUpperCase().replaceAll("\\s+", " ");
        if (!upperCleaned.startsWith("SELECT") && !upperCleaned.startsWith("WITH")) {
            throw new SqlSecurityException("仅允许SELECT查询语句，检测到: " +
                    upperCleaned.substring(0, Math.min(20, upperCleaned.length())));
        }

        // 扫描危险关键词
        if (DANGEROUS_KEYWORDS_PATTERN.matcher(cleaned).find()) {
            throw new SqlSecurityException("SQL包含禁止的关键词（不允许DML/DDL操作）");
        }

        // 扫描危险函数
        if (DANGEROUS_FUNCTIONS_PATTERN.matcher(cleaned).find()) {
            throw new SqlSecurityException("SQL包含禁止的危险函数");
        }
    }

    private String stripComments(String sql) {
        String result = BLOCK_COMMENT_PATTERN.matcher(sql).replaceAll(" ");
        result = LINE_COMMENT_PATTERN.matcher(result).replaceAll(" ");
        return result;
    }

    public static class SqlSecurityException extends RuntimeException {
        public SqlSecurityException(String message) {
            super(message);
        }
    }
}
