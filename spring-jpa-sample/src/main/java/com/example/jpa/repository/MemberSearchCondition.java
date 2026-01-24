package com.example.jpa.repository;

import com.example.jpa.entity.MemberStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * 동적 쿼리 조건 클래스
 */
@Getter
@Builder
public class MemberSearchCondition {
    private String name;
    private String email;
    private MemberStatus status;
    private String teamName;
    private Integer minOrderCount;
}
