package tech.kayys.javaland.protocol.jupyter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/** Jupyter HMAC message signing and verification helpers. */
final class JupyterSignatures {
    private JupyterSignatures() {
    }

    static String algorithmFor(String signatureScheme) {
        if (signatureScheme == null) {
            throw new IllegalArgumentException("signature_scheme must not be null");
        }
        return switch (signatureScheme.toLowerCase(Locale.ROOT)) {
            case "hmac-sha1" -> "HmacSHA1";
            case "hmac-sha224" -> "HmacSHA224";
            case "hmac-sha256" -> "HmacSHA256";
            case "hmac-sha384" -> "HmacSHA384";
            case "hmac-sha512" -> "HmacSHA512";
            default -> throw new IllegalArgumentException(
                    "Unsupported Jupyter signature scheme: " + signatureScheme);
        };
    }

    static byte[] sign(String scheme, String key, byte[]... frames) {
        if (key.isEmpty()) {
            return new byte[0];
        }
        try {
            Mac mac = Mac.getInstance(algorithmFor(scheme));
            mac.init(new SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    algorithmFor(scheme)));
            for (byte[] frame : frames) {
                mac.update(frame);
            }
            return HexFormat.of().formatHex(mac.doFinal())
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Unable to sign Jupyter message", failure);
        }
    }

    static boolean verify(String scheme, String key, byte[] signature, byte[]... frames) {
        byte[] expected = sign(scheme, key, frames);
        return MessageDigest.isEqual(expected, signature);
    }
}
