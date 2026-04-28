package es.docklite.docklitebackend.audit.entity;

import es.docklite.docklitebackend.docker.entity.ResourceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 20)
    private ResourceType resourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private ActivityAction action;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
