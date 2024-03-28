package ru.example.service.routing;

import org.apache.camel.Endpoint;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpComponent;
import org.apache.hc.core5.util.Timeout;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import ru.example.config.http.HttpClientConfig;
import ru.example.config.http.HttpConnections;
import ru.example.config.kafka.KafkaConfig;
import ru.example.exception.ZeroSecondException;
import ru.example.service.TimeService;

import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TimerRouting extends RouteBuilder {
    private final TimeService service;
    private final KafkaConfig kafkaConfig;
    private final HttpConnections httpConnections;

    private final HttpClientConfig httpClientConfig;

    public TimerRouting(TimeService service, KafkaConfig kafkaConfig, HttpConnections httpConnections, HttpClientConfig httpClientConfig) {
        this.service = service;
        this.kafkaConfig = kafkaConfig;
        this.httpConnections = httpConnections;
        this.httpClientConfig = httpClientConfig;
    }


    @Override
    public void configure() throws Exception {
        String seda = "seda:channel";
        String kafkaConnect = String.format("kafka:%s?brokers=%s&requestRequiredAcks=0", kafkaConfig.getTopic(), kafkaConfig.getHost());
        Integer poolSize = this.getCamelContext().getExecutorServiceManager().getDefaultThreadPoolProfile().getPoolSize();
        Integer maxPoolSize = this.getCamelContext().getExecutorServiceManager().getDefaultThreadPoolProfile().getMaxPoolSize();
        HttpComponent httpClientComponent = new HttpComponent();
        httpClientComponent.setSoTimeout(Timeout.of(httpClientConfig.getSocketTimeout(), TimeUnit.MILLISECONDS));
        httpClientComponent.setConnectTimeout(Timeout.of(httpClientConfig.getConnectTimeout(), TimeUnit.MILLISECONDS));
        httpClientComponent.setCamelContext(this.getCamelContext());
        Endpoint httpClientEndpoint = httpClientComponent.createEndpoint(httpConnections.getHttpServerUrl());
        AtomicBoolean atomicBoolean = new AtomicBoolean(true);

        from("timer:timer?period=1000&fixedRate=true")
                .routeId("timer")
                .process(exchange -> {
                    JSONObject currentTime = service.getCurrentTime();
                    exchange.getMessage().setBody(currentTime.toString());
                    exchange.getMessage().setHeader("second", currentTime.get("second"));
                })
                .log("before")
                .to(seda);

        from(seda)
                .routeId("Current time")
                .log("${body}")
                .doTry()
                    .choice()
                        .when(exchange -> (int) exchange.getMessage().getHeader("second") == 0)
                            .throwException(new ZeroSecondException("second == 0"))
                        .when(exchange -> (int) exchange.getMessage().getHeader("second") % 15 == 0)
                            .process(exchange -> {
                                exchange.setProperty("MessageDefault", exchange.getMessage().getBody());
                                exchange.getMessage().setBody("Hello Artem");
                            })
                            .log("Send to kafka and http-server")
                            .log(LoggingLevel.INFO, "kafka.out", "${body}")
                            .to(kafkaConnect)
                            .process(exchange -> {
                                String messageDefault = exchange.getProperty("MessageDefault", String.class);
                                exchange.getMessage().setBody(messageDefault);
                            })
                        .log(LoggingLevel.INFO, "http.out", "${body}")
                        .to(httpClientEndpoint)
                        .log(LoggingLevel.INFO, "http.in", "${body}")
                        .when(exchange -> (int) exchange.getMessage().getHeader("second") % 5 == 0)
                            .log("Send to kafka...")
                            .to("micrometer:counter:KafkaCounter")
                            .to(kafkaConnect)
                        .when(exchange -> (int) exchange.getMessage().getHeader("second") % 3 == 0)
                            .to("micrometer:timer:httpClient.timer?action=start")
                            .log("Send to http-server...")
                            .to(httpClientEndpoint)
                            .to("micrometer:timer:httpClient.timer?action=stop")
                        .otherwise()
                            .log("${body}")
                .endChoice()
                .endDoTry()
                .doCatch(ZeroSecondException.class)
                    .log("ZeroSecondException message: ${exception.message}")
                .doCatch(SocketTimeoutException.class)
                    .log("SocketTimeoutException message: ${exception.message}")
                .doCatch(Exception.class)
                    .log("Exception message: ${exception.stacktrace}")
                .end();

    }
}