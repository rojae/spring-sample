package com.example.jpa.repository;

import com.example.jpa.entity.Member;
import com.example.jpa.entity.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Repository 구현체
 *
 * 동적 쿼리 구현 방식:
 * 1. Criteria API (JPA 표준, 타입 안전)
 * 2. JPQL 문자열 조합 (간단하지만 타입 안전하지 않음)
 * 3. QueryDSL (권장, 타입 안전 + 가독성)
 */
@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private final EntityManager em;

    /**
     * JPQL 문자열 기반 동적 쿼리
     */
    @Override
    public List<Member> searchMembers(String name, MemberStatus status, String teamName) {
        StringBuilder jpql = new StringBuilder("SELECT m FROM Member m LEFT JOIN m.team t WHERE 1=1");

        if (StringUtils.hasText(name)) {
            jpql.append(" AND m.name LIKE :name");
        }
        if (status != null) {
            jpql.append(" AND m.status = :status");
        }
        if (StringUtils.hasText(teamName)) {
            jpql.append(" AND t.name = :teamName");
        }

        TypedQuery<Member> query = em.createQuery(jpql.toString(), Member.class);

        if (StringUtils.hasText(name)) {
            query.setParameter("name", "%" + name + "%");
        }
        if (status != null) {
            query.setParameter("status", status);
        }
        if (StringUtils.hasText(teamName)) {
            query.setParameter("teamName", teamName);
        }

        return query.getResultList();
    }

    /**
     * Criteria API 기반 동적 쿼리 (타입 안전)
     */
    @Override
    public List<Member> findMembersWithDynamicQuery(MemberSearchCondition condition) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Member> cq = cb.createQuery(Member.class);
        Root<Member> member = cq.from(Member.class);

        List<Predicate> predicates = new ArrayList<>();

        // 이름 조건
        if (StringUtils.hasText(condition.getName())) {
            predicates.add(cb.like(member.get("name"), "%" + condition.getName() + "%"));
        }

        // 이메일 조건
        if (StringUtils.hasText(condition.getEmail())) {
            predicates.add(cb.equal(member.get("email"), condition.getEmail()));
        }

        // 상태 조건
        if (condition.getStatus() != null) {
            predicates.add(cb.equal(member.get("status"), condition.getStatus()));
        }

        // 팀 이름 조건 (Join)
        if (StringUtils.hasText(condition.getTeamName())) {
            Join<Object, Object> team = member.join("team", JoinType.LEFT);
            predicates.add(cb.equal(team.get("name"), condition.getTeamName()));
        }

        // 최소 주문 수 조건 (Subquery)
        if (condition.getMinOrderCount() != null) {
            Subquery<Long> subquery = cq.subquery(Long.class);
            Root<Member> subMember = subquery.correlate(member);
            Join<Object, Object> orders = subMember.join("orders", JoinType.LEFT);
            subquery.select(cb.count(orders));

            predicates.add(cb.ge(subquery, condition.getMinOrderCount()));
        }

        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(member.get("createdAt")));

        return em.createQuery(cq).getResultList();
    }
}
