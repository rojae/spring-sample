package com.example.jpa.repository;

import com.example.jpa.entity.Member;
import com.example.jpa.entity.MemberStatus;

import java.util.List;

/**
 * Custom Repository Interface
 * - 복잡한 쿼리나 동적 쿼리를 위한 인터페이스
 * - QueryDSL, Criteria API, 직접 JPQL 작성 등에 활용
 */
public interface MemberRepositoryCustom {

    List<Member> searchMembers(String name, MemberStatus status, String teamName);

    List<Member> findMembersWithDynamicQuery(MemberSearchCondition condition);
}
