package com.wellbuying.domain.member.repository;

import com.wellbuying.domain.member.dto.MemberSummaryResponse;
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberQueryRepository {

    // role/status 필터로 회원 목록을 페이지 단위로 조회 (구현은 MemberQueryRepositoryImpl)
    Page<MemberSummaryResponse> search(Role role, MemberStatus status, Pageable pageable);
}
