package com.example.jpa.controller;

import com.example.jpa.entity.Member;
import com.example.jpa.entity.MemberStatus;
import com.example.jpa.repository.MemberSearchCondition;
import com.example.jpa.repository.dto.MemberSummary;
import com.example.jpa.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Member", description = "회원 관리 API")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "회원 단건 조회", description = "ID로 회원을 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<Member> findById(
            @Parameter(description = "회원 ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(memberService.findById(id));
    }

    @Operation(summary = "이메일로 회원 조회")
    @GetMapping("/email/{email}")
    public ResponseEntity<Member> findByEmail(
            @Parameter(description = "회원 이메일", example = "hong@example.com") @PathVariable String email) {
        return ResponseEntity.ok(memberService.findByEmail(email));
    }

    @Operation(summary = "회원 목록 조회 (페이징)", description = "상태별 회원 목록을 페이징하여 조회합니다.")
    @GetMapping
    public ResponseEntity<Page<Member>> findAll(
            @Parameter(description = "회원 상태", example = "ACTIVE") @RequestParam(defaultValue = "ACTIVE") MemberStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(memberService.findMembersPaging(status, pageable));
    }

    @Operation(summary = "회원 요약 정보 조회", description = "ID, 이름, 이메일만 조회합니다.")
    @GetMapping("/summaries")
    public ResponseEntity<List<MemberSummary>> findSummaries() {
        return ResponseEntity.ok(memberService.findMemberSummaries());
    }

    @Operation(summary = "회원 검색", description = "다양한 조건으로 회원을 검색합니다.")
    @GetMapping("/search")
    public ResponseEntity<List<Member>> search(
            @Parameter(description = "이름 (부분 일치)", example = "홍길동") @RequestParam(required = false) String name,
            @Parameter(description = "이메일", example = "hong@example.com") @RequestParam(required = false) String email,
            @Parameter(description = "상태", example = "ACTIVE") @RequestParam(required = false) MemberStatus status,
            @Parameter(description = "팀 이름", example = "개발팀") @RequestParam(required = false) String teamName) {

        MemberSearchCondition condition = MemberSearchCondition.builder()
                .name(name)
                .email(email)
                .status(status)
                .teamName(teamName)
                .build();

        return ResponseEntity.ok(memberService.searchMembers(condition));
    }

    @Operation(summary = "회원 생성")
    @PostMapping
    public ResponseEntity<Member> create(
            @Parameter(description = "이름", example = "홍길동") @RequestParam String name,
            @Parameter(description = "이메일", example = "hong@example.com") @RequestParam String email) {
        return ResponseEntity.ok(memberService.createMember(name, email));
    }

    @Operation(summary = "회원 정보 수정")
    @PutMapping("/{id}")
    public ResponseEntity<Member> update(
            @Parameter(description = "회원 ID", example = "1") @PathVariable Long id,
            @Parameter(description = "이름", example = "김철수") @RequestParam String name,
            @Parameter(description = "상태", example = "ACTIVE") @RequestParam MemberStatus status) {
        return ResponseEntity.ok(memberService.updateMember(id, name, status));
    }

    @Operation(summary = "회원 상태 변경")
    @PutMapping("/{id}/status")
    public ResponseEntity<Member> updateStatus(
            @Parameter(description = "회원 ID", example = "1") @PathVariable Long id,
            @Parameter(description = "상태", example = "INACTIVE") @RequestParam MemberStatus status) {
        return ResponseEntity.ok(memberService.updateMemberStatus(id, status));
    }

    @Operation(summary = "회원 팀 변경")
    @PutMapping("/{id}/team/{teamId}")
    public ResponseEntity<Member> changeTeam(
            @Parameter(description = "회원 ID", example = "1") @PathVariable Long id,
            @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId) {
        return ResponseEntity.ok(memberService.changeTeam(id, teamId));
    }

    @Operation(summary = "회원 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "회원 ID", example = "1") @PathVariable Long id) {
        memberService.deleteMember(id);
        return ResponseEntity.noContent().build();
    }
}
