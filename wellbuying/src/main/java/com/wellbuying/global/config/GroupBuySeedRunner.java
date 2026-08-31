package com.wellbuying.global.config;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.dto.GroupBuyCreateRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyCreateRequest.PriceTierRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyDetailResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartCreateRequest;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyEventOutboxRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.groupbuy.service.GroupBuyParticipationService;
import com.wellbuying.domain.groupbuy.service.GroupBuyService;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.notification.repository.NotificationRepository;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// groupbuy-seed.enabled가 true일 때만 기동 - 로컬 개발 편의용, admin-seed와 동일한 컨벤션으로 운영 환경엔 설정하지 않는다.
// 매 기동마다 seed-producer 소유의 이전 시드 데이터를 지우고 새로 채운다("꺼지면 사라지고 켜지면 다시 생긴다") -
// 진짜 셧다운 훅 대신 기동 시 리프레시 방식을 쓰는 게 더 단순하고 비정상 종료에도 안전하다.
@Component
@ConditionalOnProperty(prefix = "groupbuy-seed", name = "enabled", havingValue = "true")
public class GroupBuySeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GroupBuySeedRunner.class);

    private static final String PRODUCER_EMAIL = "seller@wellbuying.local";
    private static final String[] BUYER_EMAILS = {"buyer@wellbuying.local", "seed-buyer-2@wellbuying.local"};
    private static final String SEED_PASSWORD = "testpass1234";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPriceRepository groupBuyPriceRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyCounterRepository groupBuyCounterRepository;
    private final GroupBuyService groupBuyService;
    private final GroupBuyParticipationService groupBuyParticipationService;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final GroupBuyEventOutboxRepository groupBuyEventOutboxRepository;
    private final NotificationRepository notificationRepository;
    private final TransactionTemplate transactionTemplate;

    public GroupBuySeedRunner(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            GroupBuyRepository groupBuyRepository, GroupBuyPriceRepository groupBuyPriceRepository,
            GroupBuyPartRepository groupBuyPartRepository, GroupBuyCounterRepository groupBuyCounterRepository,
            GroupBuyService groupBuyService, GroupBuyParticipationService groupBuyParticipationService,
            ProductRepository productRepository, ProductCategoryRepository productCategoryRepository,
            GroupBuyEventOutboxRepository groupBuyEventOutboxRepository,
            NotificationRepository notificationRepository,
            PlatformTransactionManager transactionManager) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPriceRepository = groupBuyPriceRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyCounterRepository = groupBuyCounterRepository;
        this.groupBuyService = groupBuyService;
        this.groupBuyParticipationService = groupBuyParticipationService;
        this.groupBuyEventOutboxRepository = groupBuyEventOutboxRepository;
        this.notificationRepository = notificationRepository;
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // run() 자체를 @Transactional로 감싸면, seedParticipation()이 호출하는 groupBuyParticipationService.participate()가
    // (같은 트랜잭션에 REQUIRED로 합류한 채) RuntimeException을 던질 때 그 예외를 여기서 잡아도 트랜잭션은 이미
    // rollback-only로 표시되어, 정상 커밋 시점에 UnexpectedRollbackException이 나면서 앞서 만든 시드 데이터까지
    // 통째로 롤백된다. deleteByGroupBuyIdIn 같은 파생 삭제 쿼리는 트랜잭션 밖에서 실행하면 TransactionRequiredException을
    // 던지므로 그 구간만 TransactionTemplate으로 명시적으로 감싸고, seedParticipation은 트랜잭션 밖에서 호출해
    // 참여 건별로 독립적인 트랜잭션(성공/실패가 서로 영향 없음)을 갖게 한다.
    @Override
    public void run(ApplicationArguments args) {
        Long producerId = ensureMember(PRODUCER_EMAIL, "시드 생산자", true);
        List<Long> buyerIds = new ArrayList<>();
        for (String email : BUYER_EMAILS) {
            buyerIds.add(ensureMember(email, "시드 구매자", false));
        }

        List<Long> createdIds = transactionTemplate.execute(status -> {
            clearPreviousSeed(producerId);
            List<SeedProduct> seedProducts = createSeedProducts(producerId);
            return createSeedGroupBuys(producerId, seedProducts);
        });
        seedParticipation(createdIds, buyerIds);

        log.info("GroupBuySeedRunner: seeded {} group buys for producer {}", createdIds.size(), producerId);
    }

    private Long ensureMember(String email, String name, boolean asSeller) {
        return memberRepository.findByEmailAndDeletedAtIsNull(email)
                .map(Member::getId)
                .orElseGet(() -> {
                    Member member = Member.signUp(email, passwordEncoder.encode(SEED_PASSWORD), name);
                    if (asSeller) {
                        member.activateAsSeller();
                    }
                    return memberRepository.save(member).getId();
                });
    }

    private void clearPreviousSeed(Long producerId) {
        List<GroupBuy> previous = groupBuyRepository.findByProducerId(producerId);
        if (!previous.isEmpty()) {
            List<Long> ids = previous.stream().map(GroupBuy::getId).toList();
            groupBuyPartRepository.deleteByGroupBuyIdIn(ids);
            groupBuyPriceRepository.deleteByGroupBuyIdIn(ids);
            groupBuyEventOutboxRepository.deleteByGroupBuyIdIn(ids);
            notificationRepository.deleteByGroupBuyIdIn(ids);
            groupBuyCounterRepository.deleteAll(ids);
            groupBuyRepository.deleteAll(previous);
        }
        productRepository.deleteAll(productRepository.findBySellerIdOrderByIdDesc(producerId));
    }

    // 카테고리명으로 기존 카테고리를 찾고 없으면 최상위 카테고리로 새로 만든다 - 시더가 재기동돼도 카테고리는 누적되지 않는다
    private Long ensureCategoryId(Map<String, Long> categoryIdsByName, String categoryName) {
        return categoryIdsByName.computeIfAbsent(categoryName, name -> productCategoryRepository
                .findAll().stream()
                .filter(category -> category.getCategoryName().equals(name))
                .map(ProductCategory::getId)
                .findFirst()
                .orElseGet(() -> productCategoryRepository.save(ProductCategory.create(null, name)).getId()));
    }

    // 상품명은 프론트 seedCatalog.ts의 8개 장식 항목 키와 정확히 같은 문자열이어야 한다 -
    // productId는 이제 재기동마다 새로 발급돼(고정 리터럴 아님) 프론트가 매칭 키로 쓸 수 없기 때문
    private List<SeedProduct> createSeedProducts(Long producerId) {
        List<SeedItem> items = List.of(
                new SeedItem("유기농 주방 세제", "생활", "매일 쓰는 유기농 주방 세제", 5, 60,
                        -10, 6, tiers(5, 15000, 20, 12900, 60, 10900)),
                new SeedItem("천도복숭아", "식품", "산지에서 바로 오는 천도복숭아", 5, 40,
                        -15, 3, tiers(5, 24000, 15, 20000, 40, 18500)),
                new SeedItem("코튼 베이직 티셔츠", "패션", "오래 입는 코튼 베이직 티셔츠", 5, 30,
                        -20, 9, tiers(5, 29000, 15, 25000, 30, 23000)),
                new SeedItem("에티오피아 스페셜티 원두", "식품", "산미 좋은 에티오피아 스페셜티 원두", 5, 50,
                        -25, 2, tiers(5, 18000, 20, 15900, 50, 13900)),
                new SeedItem("지리산 야생화 벌꿀", "식품", "지리산 야생화 벌꿀", 5, 30,
                        -30, 14, tiers(5, 26000, 15, 23000, 30, 21000)),
                new SeedItem("고체 비누 세트", "생활", "손 씻을수록 고운 고체 비누 세트", 5, 40,
                        -5, 20, tiers(5, 22000, 15, 19000, 40, 16900)),
                new SeedItem("소이 캔들", "생활", "은은하게 오래가는 소이 캔들", 5, 20,
                        2, 16, tiers(5, 21000, 10, 18500, 20, 16500)),
                new SeedItem("스테인리스 텀블러", "생활", "하루 종일 보온되는 스테인리스 텀블러", 5, 45,
                        4, 18, tiers(5, 23000, 15, 19900, 45, 17900))
        );
        List<SeedProduct> seedProducts = new ArrayList<>();
        Map<String, Long> categoryIdsByName = new HashMap<>();
        for (SeedItem item : items) {
            Long categoryId = ensureCategoryId(categoryIdsByName, item.category());
            int startPrice = item.tiers().get(0).unitPrice();
            Product product = productRepository.save(
                    Product.register(producerId, categoryId, item.productName(), null, startPrice, null));
            seedProducts.add(new SeedProduct(item, product.getId()));
        }
        return seedProducts;
    }

    private List<Long> createSeedGroupBuys(Long producerId, List<SeedProduct> seedProducts) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = new ArrayList<>();
        for (SeedProduct seedProduct : seedProducts) {
            SeedItem item = seedProduct.item();
            LocalDateTime startAt = now.plusMinutes(item.startOffsetMinutes());
            LocalDateTime endAt = now.plusDays(item.endOffsetDays());
            GroupBuyCreateRequest request = new GroupBuyCreateRequest(seedProduct.productId(), item.title(),
                    startAt, endAt, item.minQuantity(), item.maxQuantity(), item.tiers());
            GroupBuyDetailResponse created = groupBuyService.create(producerId, request);
            ids.add(created.id());

            // 실제 시작 시각이 이미 지난 항목은 스케줄러(60초 주기)를 기다리지 않고 바로 ONGOING으로 전환해
            // 시더가 끝난 직후부터 소비자 화면에 모집 중으로 즉시 보이게 한다
            if (!startAt.isAfter(now)) {
                GroupBuy groupBuy = groupBuyRepository.findById(created.id()).orElseThrow();
                groupBuy.start();
                groupBuyRepository.save(groupBuy);
            }
        }
        return ids;
    }

    // 앞의 6개(즉시 ONGOING 전환 대상)에만 소량의 참여를 만들어 자연스러운 currentQuantity/참여자 수를 부여한다.
    // 과장된 대규모 참여자 수는 재현하지 않는다(정직하게 적은 인원).
    private void seedParticipation(List<Long> groupBuyIds, List<Long> buyerIds) {
        int[][] plan = {
                {0, 0, 4}, {0, 1, 3},
                {1, 0, 6},
                {3, 1, 5}, {3, 0, 2},
                {5, 0, 3},
        };
        for (int[] entry : plan) {
            Long groupBuyId = groupBuyIds.get(entry[0]);
            Long buyerId = buyerIds.get(entry[1]);
            int quantity = entry[2];
            try {
                groupBuyParticipationService.participate(buyerId, groupBuyId,
                        new GroupBuyPartCreateRequest(quantity));
            } catch (RuntimeException e) {
                log.warn("GroupBuySeedRunner: failed to seed participation for groupBuy {} - {}", groupBuyId,
                        e.getMessage());
            }
        }
    }

    private static List<PriceTierRequest> tiers(int t1Qty, int t1Price, int t2Qty, int t2Price, int t3Qty,
            int t3Price) {
        return List.of(
                new PriceTierRequest(1, t1Qty, t1Price),
                new PriceTierRequest(2, t2Qty, t2Price),
                new PriceTierRequest(3, t3Qty, t3Price)
        );
    }

    private record SeedItem(String productName, String category, String title, int minQuantity, int maxQuantity,
            int startOffsetMinutes, int endOffsetDays, List<PriceTierRequest> tiers) {
    }

    private record SeedProduct(SeedItem item, Long productId) {
    }
}
