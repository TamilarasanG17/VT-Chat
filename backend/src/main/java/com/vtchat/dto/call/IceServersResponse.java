package com.vtchat.dto.call;

import java.util.List;

public record IceServersResponse(List<IceServer> iceServers) {

    public record IceServer(List<String> urls, String username, String credential) {
    }
}
