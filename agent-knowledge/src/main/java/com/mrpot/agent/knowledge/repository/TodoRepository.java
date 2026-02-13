package com.mrpot.agent.knowledge.repository;

import com.mrpot.agent.common.todo.TodoItem;
import com.mrpot.agent.common.todo.TodoStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class TodoRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<TodoItem> ROW_MAPPER = (ResultSet rs, int rowNum) -> {
        Timestamp completedTs = rs.getTimestamp("completed_at");
        return new TodoItem(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("description"),
            TodoStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant().toEpochMilli(),
            completedTs != null ? completedTs.toInstant().toEpochMilli() : null
        );
    };

    public List<TodoItem> findAll() {
        return jdbcTemplate.query(
            "SELECT id, title, description, status, created_at, completed_at FROM todos ORDER BY id DESC",
            ROW_MAPPER
        );
    }

    public TodoItem findById(Long id) {
        List<TodoItem> results = jdbcTemplate.query(
            "SELECT id, title, description, status, created_at, completed_at FROM todos WHERE id = ?",
            ps -> ps.setLong(1, id),
            ROW_MAPPER
        );
        return results.isEmpty() ? null : results.getFirst();
    }

    public TodoItem create(String title, String description) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO todos (title, description, status) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, title);
            if (description != null) {
                ps.setString(2, description);
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, TodoStatus.PENDING.name());
            return ps;
        }, keyHolder);

        Long newId = ((Number) keyHolder.getKeys().get("id")).longValue();
        return findById(newId);
    }

    public TodoItem update(Long id, String title, String description, TodoStatus status) {
        TodoItem existing = findById(id);
        if (existing == null) return null;

        String newTitle = title != null ? title : existing.title();
        String newDesc = description != null ? description : existing.description();
        TodoStatus newStatus = status != null ? status : existing.status();

        String completedSql = (newStatus == TodoStatus.COMPLETED && existing.status() != TodoStatus.COMPLETED)
            ? ", completed_at = NOW()"
            : (newStatus != TodoStatus.COMPLETED ? ", completed_at = NULL" : "");

        String sql = "UPDATE todos SET title = ?, description = ?, status = ?" + completedSql + " WHERE id = ?";

        jdbcTemplate.update(sql, ps -> {
            ps.setString(1, newTitle);
            if (newDesc != null) {
                ps.setString(2, newDesc);
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, newStatus.name());
            ps.setLong(4, id);
        });

        return findById(id);
    }

    public boolean deleteById(Long id) {
        int rows = jdbcTemplate.update("DELETE FROM todos WHERE id = ?", id);
        return rows > 0;
    }
}
