package com.vtchat.dto.call;

public record CallEvent(String type, String callId, Long conversationId, Long callerId, Long receiverId,
                        String callType, CallParty caller, CallParty receiver, String sdp,
                        IceCandidate.Candidate candidate, String status, Integer duration) {

    public static final String OFFER = "CALL_OFFER";
    public static final String RINGING = "CALL_RINGING";
    public static final String ANSWER = "CALL_ANSWER";
    public static final String ICE = "CALL_ICE";
    public static final String REJECT = "CALL_REJECT";
    public static final String CANCEL = "CALL_CANCEL";
    public static final String END = "CALL_END";
    public static final String BUSY = "CALL_BUSY";
    public static final String MISSED = "CALL_MISSED";
    public static final String FAILED = "CALL_FAILED";
    public static final String HANDLED = "CALL_HANDLED";

    public static CallEvent of(String type, CallResponse call) {
        return new CallEvent(type, call.callId(), call.conversationId(), call.callerId(), call.receiverId(),
                call.callType(), null, null, null, null, call.status(), call.duration());
    }

    public static CallEvent offer(CallResponse call, CallParty caller, CallParty receiver, String sdp) {
        return new CallEvent(OFFER, call.callId(), call.conversationId(), call.callerId(), call.receiverId(),
                call.callType(), caller, receiver, sdp, null, call.status(), null);
    }

    public static CallEvent answer(CallResponse call, String sdp) {
        return new CallEvent(ANSWER, call.callId(), call.conversationId(), call.callerId(), call.receiverId(),
                call.callType(), null, null, sdp, null, call.status(), null);
    }

    public static CallEvent ice(CallResponse call, IceCandidate.Candidate candidate) {
        return new CallEvent(ICE, call.callId(), call.conversationId(), call.callerId(), call.receiverId(),
                call.callType(), null, null, null, candidate, null, null);
    }
}
