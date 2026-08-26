package com.wellbuying.domain.member.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wellbuying.domain.member.dto.MemberSummaryResponse;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.entity.QMember;
import com.wellbuying.domain.member.entity.Role;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

public class MemberQueryRepositoryImpl implements MemberQueryRepository {

    private static final QMember member = QMember.member;

    private final JPAQueryFactory queryFactory;

    public MemberQueryRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    // role/status 필터를 적용해 회원 목록을 pageable.getSort() 기준으로 조회 - 마지막 페이지 등 필요 없을 때는 count 쿼리를 생략
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
                .orderBy(sortOrders(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () -> queryFactory
                .select(member.count())
                .from(member)
                .where(roleEq(role), statusEq(status))
                .fetchOne());
    }

    // role 필터 조건 생성, role이 없으면 조건에서 제외
    private BooleanExpression roleEq(Role role) {
        return role != null ? member.role.eq(role) : null;
    }

    // status 필터 조건 생성, status가 없으면 조건에서 제외
    private BooleanExpression statusEq(MemberStatus status) {
        return status != null ? member.status.eq(status) : null;
    }

    // pageable.getSort()를 OrderSpecifier로 변환, 정렬 조건이 없으면 최신 가입순(createdAt desc)을 기본 적용
    private OrderSpecifier<?>[] sortOrders(Sort sort) {
        if (sort.isUnsorted()) {
            return new OrderSpecifier<?>[]{member.createdAt.desc()};
        }
        PathBuilder<Member> pathBuilder = new PathBuilder<>(Member.class, member.getMetadata());
        return sort.stream()
                .map(order -> new OrderSpecifier<>(
                        order.isAscending() ? Order.ASC : Order.DESC,
                        pathBuilder.getComparable(order.getProperty(), Comparable.class)))
                .toArray(OrderSpecifier<?>[]::new);
    }
}
