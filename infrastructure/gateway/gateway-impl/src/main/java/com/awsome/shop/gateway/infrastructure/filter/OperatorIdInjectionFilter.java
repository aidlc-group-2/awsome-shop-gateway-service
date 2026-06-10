package com.awsome.shop.gateway.infrastructure.filter;

import com.awsome.shop.gateway.common.constants.RouteConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Global filter to inject the authenticated {@code userId} into the POST JSON request body.
 *
 * <p>Order: +200 - executes after AuthenticationGatewayFilter.</p>
 *
 * <p>Only applies to POST requests with JSON content type that have
 * an authenticated userId available. Complements the {@code X-User-Id}
 * request header for downstream services that read identity from the body
 * (see auth-service {@code GatewayInjectableRequest}).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperatorIdInjectionFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_FIELD = "userId";

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Only inject for POST requests with JSON body
        if (!HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String userId = exchange.getAttribute(RouteConstants.ATTR_USER_ID);
        if (userId == null || userId.isEmpty()) {
            return chain.filter(exchange);
        }

        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
        if (contentType == null || !contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            return chain.filter(exchange);
        }

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(new DefaultDataBufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String body = new String(bytes, StandardCharsets.UTF_8);

                    if (body.isEmpty() || body.equals("{}")) {
                        body = "{\"" + USER_ID_FIELD + "\":\"" + userId + "\"}";
                    } else {
                        try {
                            JsonNode jsonNode = objectMapper.readTree(body);
                            if (jsonNode.isObject()) {
                                ((ObjectNode) jsonNode).put(USER_ID_FIELD, userId);
                                body = objectMapper.writeValueAsString(jsonNode);
                            }
                        } catch (JsonProcessingException e) {
                            log.warn("Failed to inject userId into request body: {}", e.getMessage());
                            return chain.filter(exchange);
                        }
                    }

                    byte[] newBodyBytes = body.getBytes(StandardCharsets.UTF_8);
                    DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
                    DataBuffer newBuffer = bufferFactory.wrap(newBodyBytes);

                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public HttpHeaders getHeaders() {
                            HttpHeaders headers = new HttpHeaders();
                            headers.putAll(super.getHeaders());
                            headers.setContentLength(newBodyBytes.length);
                            return headers;
                        }

                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.just(newBuffer);
                        }
                    };

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    @Override
    public int getOrder() {
        return 200;
    }
}
