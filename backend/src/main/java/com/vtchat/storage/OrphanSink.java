package com.vtchat.storage;

@FunctionalInterface
public interface OrphanSink {

    void record(String provider, String key, String category);

    OrphanSink NONE = (provider, key, category) -> { };
}
