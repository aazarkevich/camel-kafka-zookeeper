package ru.example.service.routing;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import ru.example.config.http.HttpServerConfig;

@Component
public class HttpServerRouting extends RouteBuilder {
    private final HttpServerConfig httpServerConfig;

    public HttpServerRouting(HttpServerConfig httpServerConfig) {
        this.httpServerConfig = httpServerConfig;
    }

    @Override
    public void configure() throws Exception {
        String jetty = String.format("jetty:http://%s:%s?",
                httpServerConfig.getHost(),
                httpServerConfig.getPort());

        from(jetty)
                .routeId("HTTP-server")
                .process(exchange -> {

                })
                .to("micrometer:timer:http-server-timer?action=start")
                .setBody(simple("Я сервер, получил сообщение ${body} в ${date:now:HH:mm:ss} отдал в "))
                .delay(10000)
                .setBody(simple("${body} ${date:now:HH:mm:ss}"))
                .log("HTTP: ${body}")
                .to("micrometer:timer:http-server-timer?action=stop");
    }
}