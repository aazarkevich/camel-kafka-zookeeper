package ru.example.config.kafka;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("kafkaconfig")
public class KafkaConfig {
    private String host;
    private String topic;
}
