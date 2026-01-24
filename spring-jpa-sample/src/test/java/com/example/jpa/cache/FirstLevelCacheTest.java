package com.example.jpa.cache;

import com.example.jpa.entity.Member;
import com.example.jpa.entity.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1차 캐시 (영속성 컨텍스트) 테스트
 *
 * 1차 캐시 특징:
 * - EntityManager(트랜잭션) 단위로 동작
 * - 같은 트랜잭션 내에서 동일 엔티티 재조회 시 DB 쿼리 없이 캐시에서 반환
 * - 동일성(identity) 보장
 * - 트랜잭션 종료 시 캐시 소멸
 */
@SpringBootTest
@ActiveProfiles("test")
class FirstLevelCacheTest {

    @PersistenceContext
    private EntityManager em;

    private Long memberId;

    @BeforeEach
    @Transactional
    void setUp() {
        Member member = Member.builder()
                .name("테스트회원")
                .email("first-level-test@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        em.persist(member);
        em.flush();
        memberId = member.getId();
        em.clear(); // 영속성 컨텍스트 초기화
    }

    @Test
    @Transactional
    @DisplayName("1차 캐시: 같은 트랜잭션 내에서 동일 엔티티 재조회 시 캐시에서 반환")
    void firstLevelCache_sameTransaction_returnsCachedEntity() {
        // given & when
        Member member1 = em.find(Member.class, memberId); // DB 조회
        Member member2 = em.find(Member.class, memberId); // 1차 캐시에서 반환

        // then
        assertThat(member1).isSameAs(member2); // 동일 객체 (==)
        assertThat(member1.getId()).isEqualTo(member2.getId());
    }

    @Test
    @Transactional
    @DisplayName("1차 캐시: 동일성(Identity) 보장")
    void firstLevelCache_guaranteesIdentity() {
        // given
        Member member1 = em.find(Member.class, memberId);

        // when - 같은 ID로 다시 조회
        Member member2 = em.find(Member.class, memberId);
        Member member3 = em.find(Member.class, memberId);

        // then - 모두 같은 객체
        assertThat(member1 == member2).isTrue();
        assertThat(member2 == member3).isTrue();
        assertThat(System.identityHashCode(member1))
                .isEqualTo(System.identityHashCode(member2))
                .isEqualTo(System.identityHashCode(member3));
    }

    @Test
    @Transactional
    @DisplayName("1차 캐시: clear() 후에는 DB에서 다시 조회")
    void firstLevelCache_afterClear_queriesDatabase() {
        // given
        Member member1 = em.find(Member.class, memberId);
        int hashCode1 = System.identityHashCode(member1);

        // when - 영속성 컨텍스트 초기화
        em.clear();
        Member member2 = em.find(Member.class, memberId); // DB에서 다시 조회
        int hashCode2 = System.identityHashCode(member2);

        // then - 다른 객체 (새로 조회됨)
        assertThat(member1).isNotSameAs(member2);
        assertThat(hashCode1).isNotEqualTo(hashCode2);
        assertThat(member1.getId()).isEqualTo(member2.getId()); // 같은 데이터
    }

    @Test
    @Transactional
    @DisplayName("1차 캐시: detach() 후에는 DB에서 다시 조회")
    void firstLevelCache_afterDetach_queriesDatabase() {
        // given
        Member member1 = em.find(Member.class, memberId);
        assertThat(em.contains(member1)).isTrue(); // 영속 상태

        // when - 준영속 상태로 전환
        em.detach(member1);
        assertThat(em.contains(member1)).isFalse(); // 준영속 상태

        Member member2 = em.find(Member.class, memberId); // DB에서 다시 조회

        // then - 다른 객체
        assertThat(member1).isNotSameAs(member2);
    }

    @Test
    @Transactional
    @DisplayName("1차 캐시: 변경 감지 (Dirty Checking)")
    void firstLevelCache_dirtyChecking() {
        // given
        Member member = em.find(Member.class, memberId);
        String originalName = member.getName();

        // when - 엔티티 수정 (별도 save 불필요)
        member.setName("변경된이름");
        em.flush(); // DB 동기화

        // then - DB에 반영 확인
        em.clear();
        Member updated = em.find(Member.class, memberId);
        assertThat(updated.getName()).isEqualTo("변경된이름");
        assertThat(updated.getName()).isNotEqualTo(originalName);
    }

    @Test
    @Transactional
    @DisplayName("1차 캐시: 쓰기 지연 (Write-Behind)")
    void firstLevelCache_writeBehind() {
        // given
        Member newMember = Member.builder()
                .name("쓰기지연테스트")
                .email("write-behind@example.com")
                .status(MemberStatus.ACTIVE)
                .build();

        // when - persist 호출 (아직 DB에 INSERT 안됨)
        em.persist(newMember);
        assertThat(newMember.getId()).isNotNull(); // ID는 할당됨

        // then - flush 전까지 1차 캐시에만 존재
        assertThat(em.contains(newMember)).isTrue();

        // flush 호출 시 DB에 INSERT
        em.flush();
    }

    @Test
    @Transactional
    @DisplayName("1차 캐시: JPQL 조회는 1차 캐시를 우회하지만 결과는 1차 캐시와 동기화")
    void firstLevelCache_jpqlBypassesButSyncs() {
        // given
        Member member1 = em.find(Member.class, memberId); // 1차 캐시에 저장

        // when - JPQL은 항상 DB 조회 (1차 캐시 우회)
        Member member2 = em.createQuery(
                        "SELECT m FROM Member m WHERE m.id = :id", Member.class)
                .setParameter("id", memberId)
                .getSingleResult();

        // then - 하지만 1차 캐시에 이미 있으면 그 객체 반환 (동일성 보장)
        assertThat(member1).isSameAs(member2);
    }
}
