package com.wellbuying.global.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

// @EnableKafka가 있어야 @KafkaListener가 실제 리스너 컨테이너로 등록된다 - 이게 없으면 컴파일/기동은 되지만
// 컨슈머가 아예 뜨지 않아 조용히 아무 메시지도 소비하지 못한다(로컬 통합 테스트로 이 문제를 직접 확인함)
@EnableKafka
@Configuration
public class KafkaConfig {

    // key/value 모두 문자열로 직렬화 - 이벤트 페이로드는 발행 직전 서비스에서 JSON 문자열로 변환해 넘긴다
    @Bean
    public ProducerFactory<String, String> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // key/value 모두 문자열로 역직렬화 - 페이로드는 각 리스너가 필요한 타입으로 직접 파싱한다.
    // group.id는 여기서 주지 않고 @KafkaListener(groupId=...)에서 리스너별로 지정한다
    // (리스너가 늘어나도 이 팩토리를 공유할 수 있도록)
    @Bean
    public ConsumerFactory<String, String> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // 컨슈머 그룹이 처음 붙는 시점엔 커밋된 오프셋이 없으므로, 그 이전 이벤트를 놓치지 않도록 처음부터 읽는다
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // 결제는 재처리 비용이 큰 작업이라 커밋 시점을 컨테이너가 제어하도록 자동 커밋을 끈다
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 일시적 장애(DB 커넥션 등)만 짧게 재시도하고, 그래도 안 되면 로그를 남기고 다음 메시지로 넘어간다.
        // 무한 재시도에 빠지면 뒤따르는 결제 건이 전부 밀리기 때문. DLT 도입은 04-failure-retry.md에서 다룬다
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 2L)));
        return factory;
    }
}
