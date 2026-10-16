// package com.vtchat.translation;

// import com.fasterxml.jackson.databind.JsonNode;
// import com.vtchat.config.TranslationProperties;
// import com.vtchat.translation.TranslationProviderException.Kind;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.MediaType;
// import org.springframework.http.client.SimpleClientHttpRequestFactory;
// import org.springframework.stereotype.Component;
// import org.springframework.web.client.ResourceAccessException;
// import org.springframework.web.client.RestClient;
// import org.springframework.web.client.RestClientException;
// import org.springframework.web.client.RestClientResponseException;

// import java.net.SocketTimeoutException;
// import java.util.LinkedHashMap;
// import java.util.Locale;
// import java.util.Map;

// /**
//  * The one class that talks to an external translation service over HTTP. Provider-specific request/response
//  * shapes live here and nowhere else. Two adapters are built in, chosen by {@code translation.provider}:
//  * <ul>
//  *   <li>{@code google}: Google Cloud Translation v2 (key sent in the {@code X-goog-api-key} header, never in the URL);</li>
//  *   <li>{@code libretranslate}: a LibreTranslate-compatible server ({@code translation.api.url} is required).</li>
//  * </ul>
//  * Failures are reduced to a {@link TranslationProviderException} category; response bodies and the key are never
//  * logged or propagated.
//  */
// @Slf4j
// @Component
// public class ExternalTranslationProvider implements TranslationProvider {

//     private static final String GOOGLE_DEFAULT_URL = "https://translation.googleapis.com/language/translate/v2";

//     private final TranslationProperties props;
//     private final RestClient client;

//     public ExternalTranslationProvider(TranslationProperties props) {
//         this.props = props;
//         SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
//         factory.setConnectTimeout(Math.min(props.getTimeout(), 5000));
//         factory.setReadTimeout(props.getTimeout());
//         this.client = RestClient.builder()
//                 .requestFactory(factory)
//                 .defaultHeader(HttpHeaders.USER_AGENT, "vtchat-backend/0.1")
//                 .build();
//         if (!isConfigured()) {
//             log.warn("Translation is not configured (set TRANSLATION_PROVIDER / TRANSLATION_API_KEY / TRANSLATION_API_URL). "
//                     + "Translate requests will answer 503 until it is.");
//         }
//     }

//     private boolean google() {
//         return !"libretranslate".equals(props.getProvider() == null ? "" : props.getProvider().trim().toLowerCase(Locale.ROOT));
//     }

//     private String baseUrl() {
//         String url = props.getApi().getUrl();
//         if (url != null && !url.isBlank()) {
//             return url.trim().replaceAll("/+$", "");
//         }
//         return google() ? GOOGLE_DEFAULT_URL : "";
//     }

//     private String apiKey() {
//         return props.getApi().getKey() == null ? "" : props.getApi().getKey().trim();
//     }

//     @Override
//     public boolean isConfigured() {
//         return google() ? !apiKey().isBlank() : !baseUrl().isBlank();
//     }

//     @Override
//     public TranslationResult translate(String text, String sourceLanguage, String targetLanguage) {
//         if (!isConfigured()) {
//             throw new TranslationProviderException(Kind.NOT_CONFIGURED);
//         }
//         return google() ? googleTranslate(text, sourceLanguage, targetLanguage)
//                 : libreTranslate(text, sourceLanguage, targetLanguage);
//     }

//     @Override
//     public LanguageDetectionResult detectLanguage(String text) {
//         if (!isConfigured()) {
//             throw new TranslationProviderException(Kind.NOT_CONFIGURED);
//         }
//         return google() ? googleDetect(text) : libreDetect(text);
//     }

//     // ------------------------------------------------------------------ Google Cloud Translation v2

//     private TranslationResult googleTranslate(String text, String source, String target) {
//         Map<String, Object> body = new LinkedHashMap<>();
//         body.put("q", text);
//         body.put("target", target);
//         body.put("format", "text");
//         if (source != null) {
//             body.put("source", source);
//         }
//         JsonNode node = post(baseUrl(), body, true);
//         JsonNode first = node.path("data").path("translations").path(0);
//         String translated = first.path("translatedText").asText(null);
//         if (translated == null) {
//             throw new TranslationProviderException(Kind.BAD_RESPONSE);
//         }
//         String detected = first.path("detectedSourceLanguage").asText(null);
//         return new TranslationResult(translated, detected);
//     }

//     private LanguageDetectionResult googleDetect(String text) {
//         JsonNode node = post(baseUrl() + "/detect", Map.of("q", text), true);
//         JsonNode first = node.path("data").path("detections").path(0).path(0);
//         String language = first.path("language").asText(null);
//         if (language == null || "und".equals(language)) {
//             return LanguageDetectionResult.unknown(null);
//         }
//         Double confidence = first.hasNonNull("confidence") ? first.get("confidence").asDouble() : null;
//         return new LanguageDetectionResult(language, confidence, null, false);
//     }

//     // ------------------------------------------------------------------ LibreTranslate

//     private TranslationResult libreTranslate(String text, String source, String target) {
//         Map<String, Object> body = new LinkedHashMap<>();
//         body.put("q", text);
//         body.put("source", source == null ? "auto" : source);
//         body.put("target", target);
//         body.put("format", "text");
//         if (!apiKey().isBlank()) {
//             body.put("api_key", apiKey());
//         }
//         JsonNode node = post(baseUrl() + "/translate", body, false);
//         String translated = node.path("translatedText").asText(null);
//         if (translated == null) {
//             throw new TranslationProviderException(Kind.BAD_RESPONSE);
//         }
//         String detected = node.path("detectedLanguage").path("language").asText(null);
//         return new TranslationResult(translated, detected);
//     }

//     private LanguageDetectionResult libreDetect(String text) {
//         Map<String, Object> body = new LinkedHashMap<>();
//         body.put("q", text);
//         if (!apiKey().isBlank()) {
//             body.put("api_key", apiKey());
//         }
//         JsonNode node = post(baseUrl() + "/detect", body, false);
//         JsonNode first = node.path(0);
//         String language = first.path("language").asText(null);
//         if (language == null) {
//             return LanguageDetectionResult.unknown(null);
//         }
//         Double confidence = first.hasNonNull("confidence") ? first.get("confidence").asDouble() / 100.0 : null;
//         return new LanguageDetectionResult(language, confidence, null, false);
//     }

//     // ------------------------------------------------------------------ HTTP

//     private JsonNode post(String url, Object body, boolean keyInHeader) {
//         try {
//             RestClient.RequestBodySpec request = client.post()
//                     .uri(url)
//                     .contentType(MediaType.APPLICATION_JSON)
//                     .accept(MediaType.APPLICATION_JSON);
//             if (keyInHeader) {
//                 request = request.header("X-goog-api-key", apiKey());
//             }
//             JsonNode node = request.body(body).retrieve().body(JsonNode.class);
//             if (node == null) {
//                 throw new TranslationProviderException(Kind.BAD_RESPONSE);
//             }
//             return node;
//         } catch (RestClientResponseException e) {
//             // Only the status code is used; the body (which could echo request data) is dropped.
//             int status = e.getStatusCode().value();
//             log.warn("Translation provider answered HTTP {}", status);
//             throw new TranslationProviderException(switch (status) {
//                 case 401, 403 -> Kind.INVALID_KEY;
//                 case 429 -> Kind.RATE_LIMITED;
//                 case 400, 404, 413, 422 -> Kind.REJECTED;
//                 default -> Kind.UNAVAILABLE;
//             });
//         } catch (ResourceAccessException e) {
//             boolean timeout = e.getCause() instanceof SocketTimeoutException;
//             log.warn("Translation provider {}", timeout ? "timed out" : "is unreachable");
//             throw new TranslationProviderException(timeout ? Kind.TIMEOUT : Kind.UNAVAILABLE);
//         } catch (TranslationProviderException e) {
//             throw e;
//         } catch (RestClientException | IllegalArgumentException e) {
//             log.warn("Translation provider call failed: {}", e.getClass().getSimpleName());
//             throw new TranslationProviderException(Kind.BAD_RESPONSE);
//         }
//     }
// }
package com.vtchat.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.vtchat.config.TranslationProperties;
import com.vtchat.translation.TranslationProviderException.Kind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class ExternalTranslationProvider implements TranslationProvider {

    private final TranslationProperties props;
    private final RestClient client;

    public ExternalTranslationProvider(TranslationProperties props) {
        this.props = props;

        TranslationProperties.Service service = props.getService();

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(service.getConnectTimeoutMs());
        factory.setReadTimeout(service.getTimeoutMs());

        this.client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "vtchat-backend/0.1"
                )
                .build();

        if (!isConfigured()) {
            log.warn(
                    "Translation service is not configured. " +
                    "Set TRANSLATION_SERVICE_URL and " +
                    "TRANSLATION_SERVICE_API_KEY."
            );
        }
    }

    @Override
    public boolean isConfigured() {
        TranslationProperties.Service service = props.getService();

        return service.getUrl() != null
                && !service.getUrl().isBlank()
                && service.getApiKey() != null
                && !service.getApiKey().isBlank();
    }

    @Override
    public TranslationResult translate(
            String text,
            String sourceLanguage,
            String targetLanguage
    ) {

        if (!isConfigured()) {
            throw new TranslationProviderException(
                    Kind.NOT_CONFIGURED
            );
        }

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("text", text);
        body.put("source_language", sourceLanguage);
        body.put("target_language", targetLanguage);

        JsonNode node = post(
                buildUrl("/translate"),
                body
        );

        String translated = node
                .path("translated_text")
                .asText(null);

        if (translated == null || translated.isBlank()) {
            throw new TranslationProviderException(
                    Kind.BAD_RESPONSE
            );
        }

        String detectedLanguage = node
                .path("detected_source_language")
                .asText(null);

        return new TranslationResult(
                translated,
                detectedLanguage
        );
    }

    @Override
    public LanguageDetectionResult detectLanguage(String text) {

        if (!isConfigured()) {
            throw new TranslationProviderException(
                    Kind.NOT_CONFIGURED
            );
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);

        JsonNode node = post(
                buildUrl("/detect-language"),
                body
        );

        String language = node
                .path("language")
                .asText(null);

        if (language == null || language.isBlank()) {
            return LanguageDetectionResult.unknown(null);
        }

        Double confidence =
                node.hasNonNull("confidence")
                        ? node.get("confidence").asDouble()
                        : null;

        return new LanguageDetectionResult(
                language,
                confidence,
                null,
                false
        );
    }

    private String buildUrl(String path) {
        String baseUrl = props.getService().getUrl()
                .trim()
                .replaceAll("/+$", "");

        return baseUrl + path;
    }

    private JsonNode post(
            String url,
            Object body
    ) {

        try {

            RestClient.RequestBodySpec request = client
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(
                            "X-Internal-Api-Key",
                            props.getService().getApiKey()
                    );

            JsonNode node = request
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (node == null) {
                throw new TranslationProviderException(
                        Kind.BAD_RESPONSE
                );
            }

            return node;

        } catch (RestClientResponseException e) {

            int status = e.getStatusCode().value();

            log.warn(
                    "Translation service answered HTTP {}",
                    status
            );

            throw new TranslationProviderException(
                    switch (status) {
                        case 401, 403 -> Kind.INVALID_KEY;
                        case 429 -> Kind.RATE_LIMITED;
                        case 400, 404, 413, 422 -> Kind.REJECTED;
                        default -> Kind.UNAVAILABLE;
                    }
            );

        } catch (ResourceAccessException e) {

            boolean timeout =
                    e.getCause() instanceof SocketTimeoutException;

            log.warn(
                    "Translation service {}",
                    timeout
                            ? "timed out"
                            : "is unreachable"
            );

            throw new TranslationProviderException(
                    timeout
                            ? Kind.TIMEOUT
                            : Kind.UNAVAILABLE
            );

        } catch (TranslationProviderException e) {

            throw e;

        } catch (RestClientException |
                 IllegalArgumentException e) {

            log.warn(
                    "Translation service call failed: {}",
                    e.getClass().getSimpleName()
            );

            throw new TranslationProviderException(
                    Kind.BAD_RESPONSE
            );
        }
    }
}