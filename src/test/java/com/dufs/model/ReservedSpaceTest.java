package com.dufs.model;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ReservedSpaceTest {

    @Test
    void serialize() {
        ReservedSpace reservedSpace = new ReservedSpace("volume".toCharArray(), 4096, 4096000);
        byte[] bytes = reservedSpace.serialize();
        assertEquals(bytes.length, 60);
        ByteBuffer bb;
        bb = ByteBuffer.wrap(Arrays.copyOf(bytes, 4));
        assertEquals(0x44554653, bb.getInt());
        byte[] name = Arrays.copyOfRange(bytes, 4, 20);
        String actualName = new String(name, StandardCharsets.UTF_16);
        assertEquals("volume\u0000\u0000", actualName);
    }
}