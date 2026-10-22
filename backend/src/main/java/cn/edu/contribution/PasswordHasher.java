package cn.edu.contribution;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

final class PasswordHasher {
    private static final int ITERATIONS = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private PasswordHasher() {}
    static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    static boolean verify(String password, String encoded) {
        try {
            String[] parts = encoded.split(":");
            return MessageDigest.isEqual(Base64.getDecoder().decode(parts[2]), derive(password, Base64.getDecoder().decode(parts[1]), Integer.parseInt(parts[0])));
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) { return false; }
    }
    private static byte[] derive(String password, byte[] salt, int iterations) {
        var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception ex) { throw new IllegalStateException("密码处理失败", ex); }
        finally { spec.clearPassword(); }
    }
}
