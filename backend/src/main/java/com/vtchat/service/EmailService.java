package com.vtchat.service;

import com.vtchat.config.AppProperties;
import com.vtchat.entity.OtpPurpose;
import com.vtchat.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmailService {

    private final AppProperties props;
    private final RestClient client;

    public EmailService(AppProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        this.client = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "vtchat-backend/0.1")
                .build();
    }

    public void sendOtp(String to, String otp, OtpPurpose purpose) {
        String apiKey = props.getResend().getApiKey();
        String from = props.getResend().getEmailFrom();
        if (apiKey == null || apiKey.isBlank() || from == null || from.isBlank()) {
            log.error("RESEND_API_KEY / EMAIL_FROM are not configured; cannot send OTP email");
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "EMAIL_NOT_CONFIGURED", "Email service is not configured on the server");
        }

        long minutes = Math.max(1, props.getOtp().getExpirationSeconds() / 60);
        Map<String, Object> payload = Map.of(
                "from", from,
                "to", List.of(to),
                "subject", subject(purpose),
                "html", html(otp, purpose, minutes),
                "text", text(otp, purpose, minutes));

        try {
            client.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("Resend rejected the email (HTTP {}): {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw ApiException.badGateway("EMAIL_SEND_FAILED", "We could not send the verification email. Please try again.");
        } catch (RestClientException e) {
            log.error("Could not reach Resend: {}", e.getMessage());
            throw ApiException.badGateway("EMAIL_SEND_FAILED", "We could not send the verification email. Please try again.");
        }
    }

    @Async
    public void sendOtpAsync(String to, String otp, OtpPurpose purpose) {
        try {
            sendOtp(to, otp, purpose);
        } catch (ApiException e) {
            log.warn("Async OTP email failed: {}", e.getCode());
        }
    }

    private String subject(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTER -> "Verify your VTChat email";
            case LOGIN -> "Your VTChat login code";
            default -> "Reset your VTChat password";
        };
    }

    private String intro(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTER -> "Use this code to verify your email and finish creating your VTChat account.";
            case LOGIN -> "Use this code to finish logging in to VTChat.";
            default -> "Use this code to reset your VTChat password.";
        };
    }

    private String text(String otp, OtpPurpose purpose, long minutes) {
        return intro(purpose) + "\n\nYour code: " + otp + "\n\nIt expires in " + minutes
                + " minutes and can only be used once. If you didn't request it, you can ignore this email.";
    }

    private String html(String otp, OtpPurpose purpose, long minutes) {
        return """
                <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;max-width:420px;margin:0 auto;padding:24px;color:#0f172a">
                  <h2 style="margin:0 0 8px;color:#4f46e5">VTChat</h2>
                  <p style="margin:0 0 20px;line-height:1.5">%s</p>
                  <div style="font-size:34px;font-weight:700;letter-spacing:10px;text-align:center;background:#eef2ff;border-radius:12px;padding:16px 0;color:#3730a3">%s</div>
                  <p style="margin:20px 0 0;font-size:13px;color:#475569;line-height:1.5">This code expires in %d minutes and can only be used once. If you didn't request it, you can safely ignore this email.</p>
                </div>
                """.formatted(intro(purpose), otp, minutes);
    }
}
