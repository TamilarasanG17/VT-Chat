package com.vtchat.translation;

import com.vtchat.config.TranslationProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

@Slf4j
@Component
public class TranslationServiceClient {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";

    public record Result(String sourceLanguage, String sourceScript, boolean romanized, String targetLanguage, String translatedText) {
    }

    private record Request(String text, String targetLanguage) {
    }

    private final TranslationProperties props;
    private RestClient client;

    public TranslationServiceClient(TranslationProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        TranslationProperties.Service s = props.getService();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(s.getConnectTimeoutMs());
        factory.setReadTimeout(s.getTimeoutMs());
        this.client = RestClient.builder()
                .baseUrl(s.getUrl())
                .requestFactory(factory)
                .build();
        if (s.getApiKey() == null || s.getApiKey().isBlank()) {
            log.warn("TRANSLATION_SERVICE_API_KEY is not set: translation requests will be refused by the translation service");
        }
    }

    public Result translate(String text, String targetLanguage) {
        try {
            Result result = client.post()
                    .uri("/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(API_KEY_HEADER, props.getService().getApiKey())
                    .body(new Request(text, targetLanguage))
                    .retrieve()
                    .body(Result.class);
            if (result == null || result.translatedText() == null) {
                throw new TranslationServiceException(TranslationServiceException.Kind.BAD_RESPONSE, "empty response");
            }
            return result;
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new TranslationServiceException(TranslationServiceException.Kind.AUTH, "translation service rejected the API key");
            }
            throw new TranslationServiceException(TranslationServiceException.Kind.REJECTED, "translation service rejected the request (" + status + ")");
        } catch (HttpServerErrorException e) {
            int status = e.getStatusCode().value();
            throw new TranslationServiceException(status == 503 || status == 502 || status == 504
                    ? TranslationServiceException.Kind.UNAVAILABLE : TranslationServiceException.Kind.BAD_RESPONSE,
                    "translation service error (" + status + ")");
        } catch (ResourceAccessException e) {
            boolean timeout = e.getCause() instanceof SocketTimeoutException;
            throw new TranslationServiceException(timeout ? TranslationServiceException.Kind.TIMEOUT : TranslationServiceException.Kind.UNAVAILABLE,
                    timeout ? "translation service timed out" : "translation service is not reachable");
        } catch (TranslationServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TranslationServiceException(TranslationServiceException.Kind.BAD_RESPONSE, "unreadable response from translation service");
        }
    }
}
