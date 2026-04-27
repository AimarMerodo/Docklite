package es.docklite.docklitebackend.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;


@Entity
@Table(name = "users")
@Data
// PROTECTED: solo JPA puede hacer "new User()" vacío, otra parte del codigo no.
// PROTECTED: JPA/Hibernate lo necesita para reconstruir users desde BBDD. No usar desde fuera.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// PRIVATE: solo Lombok lo usa por dentro para que el @Builder funcione.
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User {
    // Long (mayúscula) para que valga null antes de guardarse.
    // Si fuera long primitivo valdría 0 por defecto y JPA lo confundiría
    // con un user ya persistido en lugar de uno nuevo.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    // 255 es un valor por defecto lo dejo para que me quede mas claro a mi pero no haria falta
    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // se ejecuta justo antes del INSERT (la primera vez que se guarda un objeto nuevo)
    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
        if (role == null) role = Role.USER;
    }

    // se ejecuta justo antes de cada UPDATE (cada vez que modificas un objeto ya persistido).
    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }



}
