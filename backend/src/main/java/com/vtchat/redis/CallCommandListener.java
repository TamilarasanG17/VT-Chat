package com.vtchat.redis;

public interface CallCommandListener {

    void onCallCommand(DistributedEvent command);
}
