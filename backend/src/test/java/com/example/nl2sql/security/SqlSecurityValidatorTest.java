package com.example.nl2sql.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlSecurityValidatorTest {

    private SqlSecurityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SqlSecurityValidator();
    }

    @Test
    void shouldAllowSimpleSelect() {
        assertDoesNotThrow(() -> validator.validate("SELECT * FROM users"));
    }

    @Test
    void shouldAllowSelectWithWhere() {
        assertDoesNotThrow(() -> validator.validate("SELECT name, age FROM users WHERE age > 30"));
    }

    @Test
    void shouldAllowSelectWithJoin() {
        assertDoesNotThrow(() -> validator.validate(
                "SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id"));
    }

    @Test
    void shouldAllowWithCTE() {
        assertDoesNotThrow(() -> validator.validate(
                "WITH cte AS (SELECT id FROM users) SELECT * FROM cte"));
    }

    @Test
    void shouldBlockInsert() {
        assertThrows(SqlSecurityValidator.SqlSecurityException.class,
                () -> validator.validate("INSERT INTO users (name) VALUES ('test')"));
    }

    @Test
    void shouldBlockUpdate() {
        assertThrows(SqlSecurityValidator.SqlSecurityException.class,
                () -> validator.validate("UPDATE users SET name = 'test' WHERE id = 1"));
    }

    @Test
    void shouldBlockDelete() {
        assertThrows(SqlSecurityValidator.SqlSecurityException.class,
                () -> validator.validate("DELETE FROM users WHERE id = 1"));
    }

    @Test
    void shouldBlockDrop() {
        assertThrows(SqlSecurityValidator.SqlSecurityException.class,
                () -> validator.validate("DROP TABLE users"));
    }

    @Test
    void shouldBlockTruncate() {
        assertThrows(SqlSecurityValidator.SqlSecurityException.class,
                () -> validator.validate("TRUNCATE TABLE users"));
    }

    @Test
    void shouldBlockMultipleStatements() {
        assertThrows(SqlSecurityValidator.SqlSecurityException.class,
                () -> validator.validate("SELECT 1; DROP TABLE users"));
    }

    @Test
    void shouldBlockSqlWithCommentInjection() {
        // 即使危险部分在注释后，多语句检查也会捕获
        assertThrows(SqlSecurityValidator.SqlSecurityException.class,
                () -> validator.validate("SELECT 1; /* hidden */ DROP TABLE users"));
    }

    @Test
    void shouldBlockEmptySql() {
        assertThrows(SqlSecurityValidator.SqlSecurityException.class,
                () -> validator.validate(""));
    }

    @Test
    void shouldBlockNullSql() {
        assertThrows(SqlSecurityValidator.SqlSecurityException.class,
                () -> validator.validate(null));
    }

    @Test
    void shouldBlockDangerousFunctions() {
        assertThrows(SqlSecurityValidator.SqlSecurityException.class,
                () -> validator.validate("SELECT SLEEP(10)"));
    }

    @Test
    void shouldAllowSelectWithSubquery() {
        assertDoesNotThrow(() -> validator.validate(
                "SELECT name FROM users WHERE id IN (SELECT user_id FROM orders WHERE total > 100)"));
    }

    @Test
    void shouldAllowSelectWithAggregation() {
        assertDoesNotThrow(() -> validator.validate(
                "SELECT COUNT(*), AVG(age) FROM users GROUP BY department HAVING COUNT(*) > 5"));
    }
}
