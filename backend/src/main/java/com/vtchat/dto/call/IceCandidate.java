package com.vtchat.dto.call;

public record IceCandidate(String callId, Candidate candidate) {

    public record Candidate(String candidate, String sdpMid, Integer sdpMLineIndex, String usernameFragment) {
    }
}
