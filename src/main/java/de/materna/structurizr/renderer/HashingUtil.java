package de.materna.structurizr.renderer;


import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HashingUtil {

    public static String buildHash(Path workspacePath, Path workspaceJsonPath, String viewKey, String renderer) {
        return HashingUtil.sha256HexConcat(md -> {
            // Renderer + Version
            md.update(normalize("renderer=" + renderer));

            // View
            md.update(normalize("viewKey=" + viewKey));

            // Workspace mtime
            long wsMtime = 0;
            try { wsMtime = Files.getLastModifiedTime(workspacePath).toMillis(); } catch (Exception ignore) {}
            md.update(normalize("wsPath=" + workspacePath.toAbsolutePath()));
            md.update(normalize("wsMtime=" + wsMtime));

            // Layout mtime
            if (workspaceJsonPath != null) {
                long layoutMtime = 0;
                try { layoutMtime = Files.getLastModifiedTime(workspaceJsonPath).toMillis(); } catch (Exception ignore) {}
                md.update(normalize("wsJsonPath=" + workspaceJsonPath.toAbsolutePath()));
                md.update(normalize("wsJsonMtime=" + layoutMtime));
            }
        });
    }

    public static byte[] normalize(String text) {
        if (text == null) return new byte[0];
        // Nur Zeilenenden vereinheitlichen; keine aggressive Unicode-Normalisierung
        return text.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256HexConcat(Consumer<MessageDigest> feeder) {
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

