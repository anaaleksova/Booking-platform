package com.bookingsaas.staff;

import com.bookingsaas.common.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A person who delivers services.
 *
 * <p>{@code userId} is nullable because most staff never log in: an owner can
 * add "Ana" to the schedule without creating an account for her.
 */
@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
public class Staff extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 320)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(name = "photo_url", length = 1024)
    private String photoUrl;

    @Column(nullable = false)
    private boolean active = true;
}
