package com.wellbuying.domain.member.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wellbuying.domain.member.dto.MemberSummaryResponse;
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.entity.QMember;
import com.wellbuying.domain.member.entity.Role;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class MemberQueryRepositoryImpl implements MemberQueryRepository {

    private static final QMember member = QMember.member;

    private final JPAQueryFactory queryFactory;

    public MemberQueryRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    // role/status 필터를 적용해 회원 목록을 최신 가입순으로 조회, 별도 count 쿼리로 전체 개수 확인
    @Override
    public Page<MemberSummaryResponse> search(Role role, MemberStatus status, Pageable pageable) {
        List<MemberSummaryResponse> content = queryFactory
                .select(Projections.constructor(MemberSummaryResponse.class,
                        member.id,
                        member.email,
                        member.name,
                        member.role,
                        member.status,
                        member.phoneNumber,
                        member.createdAt))
                .from(member)
                .where(roleEq(role), statusEq(status))
                .orderBy(member.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(member.count())
                .from(member)
                .where(roleEq(role), statusEq(status))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    // role 필터 조건 생성, role이 없으면 조건에서 제외
    private BooleanExpression roleEq(Role role) {
        return role != null ? member.role.eq(role) : null;
    }

    // status 필터 조건 생성, status가 없으면 조건에서 제외
    private BooleanExpression statusEq(MemberStatus status) {
        return status != null ? member.status.eq(status) : null;
    }
}
