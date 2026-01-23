package io.github.stephanpirnbaum.structurizr.renderer;


import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HashingUtil {

    public static byte[] normalize(String text) {
        if (text == null) return new byte[0];
        // Nur Zeilenenden vereinheitlichen; keine aggressive Unicode-Normalisierung
        return text.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static String sha256HexConcat(Consumer<MessageDigest> feeder) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            feeder.accept(md);
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16))
                    .append(Character.forDigit((b & 0xF), 16));
        }
        return sb.toString();
    }
}

