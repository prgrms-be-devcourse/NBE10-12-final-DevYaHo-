package com.wellbuying.domain.payment.crypto;

// 빌링키 암복호화 경계.
// 지금은 .env의 마스터 키를 쓰는 EnvKeyAesGcmEncryptor 하나뿐이지만, 운영 전환 시 KMS 봉투 암호화
// (KmsEnvelopeEncryptor)로 갈아끼울 수 있도록 인터페이스로 분리해 둔다 - 저장 형식이 바뀌어도
// 이 인터페이스를 쓰는 쪽(BillingKeyService, DbBillingKeyProvider)은 손대지 않는다
public interface BillingKeyEncryptor {

    String encrypt(String plainText);

    String decrypt(String cipherText);
}
