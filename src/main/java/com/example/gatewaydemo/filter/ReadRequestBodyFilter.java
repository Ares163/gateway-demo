package com.example.gatewaydemo.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * record request body
 * @author liang
 * @date 19/3/6
 */
@Component
@Slf4j
public class ReadRequestBodyFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        List<HttpMessageReader<?>> messageReaders = HandlerStrategies.withDefaults().messageReaders();

        if (HttpMethod.GET.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        Mono<String> cachedRequestBodyObject =
                DataBufferUtils.join(exchange.getRequest().getBody()).flatMap((dataBuffer) -> {
                    DataBufferUtils.retain(dataBuffer);
                    final Flux<DataBuffer> cachedFlux =
                            Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));

                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return cachedFlux;
                        }
                    };
                    return ServerRequest
                            .create(exchange.mutate().request(mutatedRequest).build(), messageReaders)
                            .bodyToMono(String.class)
                            .doOnNext((objectValue) -> {
                                exchange.getAttributes().put("cachedRequestBodyObject", objectValue);
                                exchange.getAttributes().put("cachedRequestBody", cachedFlux);
                            });
                });

        return cachedRequestBodyObject.flatMap(formData -> {
            return chain.filter(exchange);
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
