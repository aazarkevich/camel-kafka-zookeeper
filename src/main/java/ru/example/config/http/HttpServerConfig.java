package ru.example.config.http;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Getter
@Setter
@Component
@ConfigurationProperties("httpserverconfig")
public class HttpServerConfig {
    private String host;
    private String port;
}
