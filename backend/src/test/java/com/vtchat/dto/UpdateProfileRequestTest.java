package com.vtchat.dto;

import com.vtchat.dto.user.UpdateProfileRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UpdateProfileRequestTest {

    @Test
    void trimsUsernameAndTurnsBlankBioIntoNull() {
        UpdateProfileRequest r = new UpdateProfileRequest("  alice  ", "   ");
        assertEquals("alice", r.username());
        assertNull(r.bio());
    }

    @Test
    void stripsControlCharactersFromBioButKeepsNewLines() {
        UpdateProfileRequest r = new UpdateProfileRequest("alice", " hi\u0007 there\r\nline two ");
        assertEquals("hi there\nline two", r.bio());
    }
}
