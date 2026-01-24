package com.example.jpa.service;

import com.example.jpa.entity.Member;
import com.example.jpa.entity.MemberStatus;
import com.example.jpa.entity.Team;
import com.example.jpa.repository.MemberRepository;
import com.example.jpa.repository.MemberSearchCondition;
import com.example.jpa.repository.TeamRepository;
import com.example.jpa.repository.dto.MemberSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MemberService - Spring Cache 어노테이션 활용 예제
 *
 * Spring Cache 어노테이션:
 * - @Cacheable: 캐시에서 조회, 없으면 실행 후 저장
 * - @CachePut: 항상 실행 후 캐시 갱신
 * - @CacheEvict: 캐시 제거
 * - @Caching: 여러 캐시 어노테이션 조합
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final TeamRepository teamRepository;

    // ==================== Spring Cache (@Cacheable) ====================

    /**
     * @Cacheable - 캐시 조회, 없으면 실행 후 저장
     * - value: 캐시 이름
     * - key: 캐시 키 (SpEL 표현식)
     * - condition: 캐시 조건
     * - unless: 결과 기반 캐시 제외 조건
     */
    @Cacheable(value = "members", key = "#id")
    public Member findById(Long id) {
        log.info("DB에서 Member 조회: {}", id);
        return memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }

    @Cacheable(value = "members", key = "#email")
    public Member findByEmail(String email) {
        log.info("DB에서 Member 조회 (email): {}", email);
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }

    /**
     * 조건부 캐싱
     * - condition: 실행 전 평가, true일 때만 캐시 조회/저장
     * - unless: 실행 후 평가, true일 때 캐시 저장 안함
     */
    @Cacheable(
            value = "membersByStatus",
            key = "#status.name()",
            condition = "#status != T(com.example.jpa.entity.MemberStatus).SUSPENDED",
            unless = "#result.isEmpty()"
    )
    public List<Member> findByStatus(MemberStatus status) {
        log.info("DB에서 상태별 Member 조회: {}", status);
        return memberRepository.findByStatus(status);
    }

    // ==================== Spring Cache (@CachePut) ====================

    /**
     * @CachePut - 항상 실행 후 캐시 갱신
     * - 수정 후 최신 데이터로 캐시 업데이트
     */
    @Transactional
    @CachePut(value = "members", key = "#result.id")
    public Member createMember(String name, String email) {
        log.info("Member 생성: {}", email);

        Member member = Member.builder()
                .name(name)
                .email(email)
                .status(MemberStatus.ACTIVE)
                .build();

        return memberRepository.save(member);
    }

    @Transactional
    @CachePut(value = "members", key = "#id")
    public Member updateMember(Long id, String name, MemberStatus status) {
        log.info("Member 수정: {}", id);

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        member.setName(name);
        member.setStatus(status);

        return member;  // 변경 감지로 자동 저장
    }

    // ==================== Spring Cache (@CacheEvict) ====================

    /**
     * @CacheEvict - 캐시 제거
     * - allEntries: 해당 캐시의 모든 엔트리 제거
     * - beforeInvocation: 메서드 실행 전에 캐시 제거
     */
    @Transactional
    @CacheEvict(value = "members", key = "#id")
    public void deleteMember(Long id) {
        log.info("Member 삭제: {}", id);
        memberRepository.deleteById(id);
    }

    @Transactional
    @CacheEvict(value = "membersByStatus", allEntries = true)
    public void deleteAllInactiveMembers() {
        log.info("비활성 회원 전체 삭제");
        memberRepository.deleteByStatusBulk(MemberStatus.INACTIVE);
    }

    // ==================== Spring Cache (@Caching) ====================

    /**
     * @Caching - 여러 캐시 작업 조합
     */
    @Transactional
    @Caching(
            put = {@CachePut(value = "members", key = "#result.id")},
            evict = {@CacheEvict(value = "membersByStatus", allEntries = true)}
    )
    public Member updateMemberStatus(Long id, MemberStatus status) {
        log.info("Member 상태 변경: {} -> {}", id, status);

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        member.setStatus(status);
        return member;
    }

    // ==================== 비즈니스 로직 ====================

    /**
     * Fetch Join을 사용한 N+1 문제 해결
     */
    public Member findByIdWithTeam(Long id) {
        return memberRepository.findByIdWithTeam(id)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }

    /**
     * EntityGraph를 사용한 N+1 문제 해결
     */
    public Member findByIdWithGraph(Long id) {
        return memberRepository.findByIdWithGraph(id)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }

    /**
     * 페이징 조회
     */
    public Page<Member> findMembersPaging(MemberStatus status, Pageable pageable) {
        return memberRepository.findByStatus(status, pageable);
    }

    /**
     * DTO Projection - 필요한 컬럼만 조회
     */
    @Cacheable(value = "members", key = "'summaries'")
    public List<MemberSummary> findMemberSummaries() {
        log.info("Member 요약 정보 조회");
        return memberRepository.findMemberSummaries();
    }

    /**
     * 동적 쿼리
     */
    public List<Member> searchMembers(MemberSearchCondition condition) {
        return memberRepository.findMembersWithDynamicQuery(condition);
    }

    /**
     * 벌크 연산 (직접 UPDATE)
     */
    @Transactional
    @CacheEvict(value = {"members", "membersByStatus"}, allEntries = true)
    public int bulkUpdateStatus(List<Long> ids, MemberStatus status) {
        return memberRepository.bulkUpdateStatus(ids, status);
    }

    /**
     * 팀 변경
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "members", key = "#memberId"),
            @CacheEvict(value = "teams", allEntries = true)
    })
    public Member changeTeam(Long memberId, Long teamId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다."));

        member.changeTeam(team);
        return member;
    }
}
