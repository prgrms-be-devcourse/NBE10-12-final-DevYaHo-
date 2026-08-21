package com.wellbuying.auth.oauth;

import java.util.Map;

record OAuthUserInfo(String providerId, String email, String name, String profileImage) {

    // provider별 응답 형태에 맞춰 파싱 - Google은 평탄한 속성(sub/email/name/picture), Kakao는 중첩된 kakao_account/properties
    static OAuthUserInfo of(String provider, Map<String, Object> attributes) {
        if ("kakao".equals(provider)) {
            return ofKakao(attributes);
        }
        return ofGoogle(attributes);
    }

    private static OAuthUserInfo ofGoogle(Map<String, Object> attributes) {
        return new OAuthUserInfo(
                String.valueOf(attributes.get("sub")),
                (String) attributes.get("email"),
                (String) attributes.get("name"),
                (String) attributes.get("picture"));
    }

    @SuppressWarnings("unchecked")
    private static OAuthUserInfo ofKakao(Map<String, Object> attributes) {
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
        return new OAuthUserInfo(
                String.valueOf(attributes.get("id")),
                kakaoAccount != null ? (String) kakaoAccount.get("email") : null,
                properties != null ? (String) properties.get("nickname") : null,
                properties != null ? (String) properties.get("profile_image") : null);
    }
}
