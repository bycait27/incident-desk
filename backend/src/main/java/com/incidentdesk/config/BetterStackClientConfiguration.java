package com.incidentdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class BetterStackClientConfiguration {

    private static final int CONNECT_TIMEOUT_MILLIS = 2_000;
    private static final int READ_TIMEOUT_MILLIS = 3_000;

    @Bean
    public RestTemplate betterStackRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        return new RestTemplate(requestFactory);
    }
}
