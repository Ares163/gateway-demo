package com.example.gatewaydemo.filter;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.Charset;

/**
 * @author liang
 * @date 19/2/26
 */
@Component
@Slf4j
public class AccessFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String info = String.format("Method:%s Host:%s Path:%s Query:%s",
                exchange.getRequest().getMethod().name(),
                exchange.getRequest().getURI().getHost(),
                exchange.getRequest().getURI().getPath(),
                exchange.getRequest().getQueryParams());

        log.info(info);

        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    return super.writeWith(fluxBody.map(dataBuffer -> {
                        byte[] content = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(content);
                        DataBufferUtils.release(dataBuffer);
                        String s = new String(content, Charset.forName("UTF-8"));
                        log.info("response:" + s);
                        exchange.getAttributes().put("responseBody", s);
                        return bufferFactory.wrap(content);
                    }));
                }
                return super.writeWith(body);
            }
        };

        ServerWebExchange ex = exchange.mutate()
                .response(decoratedResponse)
                .build();
//        ServerWebExchange ex = exchange;

        return chain.filter(ex).doAfterSuccessOrError((r, t) -> {
            String uri = ex.getRequest().getURI().getRawPath();
            // to get non-standard status code or to set non-standard status code, should use AbstractServerHttpResponse
            String status = String.valueOf(((AbstractServerHttpResponse) ex.getResponse()).getStatusCodeValue());

            //set non-standard status code
//            ((AbstractServerHttpResponse) ex.getResponse()).setStatusCodeValue(461);

            String request = exchange.getAttribute("cachedRequestBodyObject");
            String responseBody = exchange.getAttribute("responseBody");

            log.info("access||uri={}||request={}||responseBody={}||status={}", uri, request, responseBody, status);
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
