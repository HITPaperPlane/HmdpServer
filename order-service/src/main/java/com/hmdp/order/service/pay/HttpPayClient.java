package com.hmdp.order.service.pay;

import com.hmdp.order.dto.pay.PayCloseResult;
import com.hmdp.order.dto.pay.PayStatusResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class HttpPayClient implements PayClient {

    private final RestTemplate restTemplate;

    @Value("${pay-service.base-url:http://127.0.0.1:8090}")
    private String baseUrl;

    @Override
    public PayStatusResult queryPayStatus(Long orderId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/pay/query")
                .queryParam("orderId", orderId)
                .toUriString();
        try {
            ResponseEntity<PayStatusResult> resp = restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, PayStatusResult.class);
            return resp.getBody();
        } catch (RestClientException e) {
            throw new IllegalStateException("call pay-service query failed", e);
        }
    }

    @Override
    public PayCloseResult closeTrade(Long orderId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/pay/close")
                .queryParam("orderId", orderId)
                .toUriString();
        try {
            ResponseEntity<PayCloseResult> resp = restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, PayCloseResult.class);
            return resp.getBody();
        } catch (RestClientException e) {
            throw new IllegalStateException("call pay-service close failed", e);
        }
    }
}

