package com.wellbuying.domain.member.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.member.service.ProfileImageUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectTaggingRequest;

@ExtendWith(MockitoExtension.class)
class ProfileImageEventListenerTest {

    private static final String BUCKET = "wellbuying-dev";

    @Mock
    private S3Client s3Client;

    @Mock
    private ProfileImageUploadService profileImageUploadService;

    private ProfileImageEventListener profileImageEventListener;

    @BeforeEach
    void setUp() {
        profileImageEventListener = new ProfileImageEventListener(s3Client, profileImageUploadService, BUCKET);
    }

    // 저장이 확정되면(PATCH로 저장 완료) pending 태그를 제거해 lifecycle rule의 자동 만료 대상에서 제외하는지 검증
    @Test
    void 저장이_확정되면_pending_태그를_제거한다() {
        String imageUrl = "https://wellbuying-dev.s3.ap-northeast-2.amazonaws.com/profile-images/1/new.jpg";
        when(profileImageUploadService.isOurBucketUrl(imageUrl)).thenReturn(true);
        when(profileImageUploadService.extractKey(imageUrl)).thenReturn("profile-images/1/new.jpg");

        profileImageEventListener.handleConfirmed(new ProfileImageConfirmedEvent(imageUrl));

        verify(s3Client).deleteObjectTagging(
                DeleteObjectTaggingRequest.builder().bucket(BUCKET).key("profile-images/1/new.jpg").build());
    }

    // 우리 버킷 URL이면 더 이상 참조되지 않는 이전 이미지를 삭제하는지 검증
    @Test
    void 우리_버킷_URL이면_S3_객체를_삭제한다() {
        String imageUrl = "https://wellbuying-dev.s3.ap-northeast-2.amazonaws.com/profile-images/1/old.jpg";
        when(profileImageUploadService.isOurBucketUrl(imageUrl)).thenReturn(true);
        when(profileImageUploadService.extractKey(imageUrl)).thenReturn("profile-images/1/old.jpg");

        profileImageEventListener.handleOrphaned(new ProfileImageOrphanedEvent(imageUrl));

        verify(s3Client)
                .deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key("profile-images/1/old.jpg").build());
    }

    // 외부 CDN URL(OAuth 프로필 이미지 등)이면 발행 측 필터링과 별개로 리스너에서도 삭제를 시도하지 않는지 검증
    @Test
    void 외부_CDN_URL이면_삭제를_시도하지_않는다() {
        String imageUrl = "https://k.kakaocdn.net/dn/profile.jpg";
        when(profileImageUploadService.isOurBucketUrl(imageUrl)).thenReturn(false);

        profileImageEventListener.handleOrphaned(new ProfileImageOrphanedEvent(imageUrl));

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
