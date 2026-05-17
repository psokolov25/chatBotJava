package ru.qsystems.telegrambot.path;

import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class ScriptArchiveStore {
    private final DataSource dataSource;

    public ScriptArchiveStore(DataSource dataSource) {
        this.dataSource = dataSource;
        initSchema();
    }

    public void saveArchive(String fileName, byte[] content, Instant uploadedAt) {
        String sql = "merge into script_archives key(file_name) values (?, ?, ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fileName);
            ps.setBytes(2, content);
            ps.setTimestamp(3, Timestamp.from(uploadedAt));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save script archive to H2", e);
        }
    }

    public List<ArchiveRecord> findAll() {
        String sql = "select file_name, content, uploaded_at from script_archives";
        List<ArchiveRecord> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new ArchiveRecord(
                        rs.getString("file_name"),
                        rs.getBytes("content"),
                        rs.getTimestamp("uploaded_at").toInstant()
                ));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read script archives from H2", e);
        }
    }

    private void initSchema() {
        String sql = "create table if not exists script_archives(file_name varchar(255) primary key, content blob not null, uploaded_at timestamp not null)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize script_archives table", e);
        }
    }

    public record ArchiveRecord(String fileName, byte[] content, Instant uploadedAt) {}
}
