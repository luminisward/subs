package xyz.monado.subs;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;

@Component
public class RequestUtil {
    private final WebClient webClient;

    public RequestUtil(WebClient webClient) {
        this.webClient = webClient;
    }
    @Cacheable(value = "request", key = "#uri.toString() + #clientType")
    public ResponseEntity<String> requestRemoteConfig(URI uri, ClientType clientType) {
        var requestBuilder = webClient.get().uri(uri);
        return requestBuilder.header(HttpHeaders.USER_AGENT, clientType.name()).retrieve().toEntity(String.class).block();
    }
}
