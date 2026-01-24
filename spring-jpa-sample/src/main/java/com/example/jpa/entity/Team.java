package com.example.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Team Entity - 읽기 전용 캐시 예제
 */
@Entity
@Table(name = "teams")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY, region = "teamCache")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @OneToMany(mappedBy = "team")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @Builder.Default
    private List<Member> members = new ArrayList<>();
}
