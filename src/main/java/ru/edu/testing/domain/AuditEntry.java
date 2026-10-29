package ru.edu.testing.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Запись журнала действий: кто, когда и что сделал. */
@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 500)
    private String details;

    public AuditEntry() {
    }

    public AuditEntry(String username, String action, String details) {
        this.createdAt = LocalDateTime.now();
        this.username = username;
        this.action = action;
        this.details = details == null ? null : details.length() > 500 ? details.substring(0, 500) : details;
    }

    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
}
