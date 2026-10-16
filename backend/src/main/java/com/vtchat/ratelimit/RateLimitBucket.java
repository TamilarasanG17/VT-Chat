package com.vtchat.ratelimit;

import com.vtchat.config.RateLimitProperties;

import java.util.function.Function;

public enum RateLimitBucket {
    AUTH("auth", RateLimitProperties::getAuth),
    OTP("otp", RateLimitProperties::getOtp),
    SEARCH("search", RateLimitProperties::getSearch),
    MESSAGE("message", RateLimitProperties::getMessage),
    MEDIA("media", RateLimitProperties::getMedia),
    TRANSLATION("translation", RateLimitProperties::getTranslation),
    CALL_SIGNAL("call-signal", RateLimitProperties::getCallSignal),
    CALL_ICE("call-ice", RateLimitProperties::getCallIce),
    NOTIFICATION("notification", RateLimitProperties::getNotification),
    CHAT_ACTION("chat-action", RateLimitProperties::getChatAction),
    TYPING("typing", RateLimitProperties::getTyping),
    AI("ai", RateLimitProperties::getAi),
    RAG_UPLOAD("rag-upload", RateLimitProperties::getRagUpload),
    RAG_REPROCESS("rag-reprocess", RateLimitProperties::getRagReprocess),
    RAG_QUERY("rag-query", RateLimitProperties::getRagQuery);

    private final String keyName;
    private final Function<RateLimitProperties, RateLimitProperties.Rule> rule;

    RateLimitBucket(String keyName, Function<RateLimitProperties, RateLimitProperties.Rule> rule) {
        this.keyName = keyName;
        this.rule = rule;
    }

    public String keyName() {
        return keyName;
    }

    public RateLimitProperties.Rule ruleFrom(RateLimitProperties props) {
        return rule.apply(props);
    }
}
