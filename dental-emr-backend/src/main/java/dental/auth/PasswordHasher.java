package dental.auth;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordHasher {
    private static final int ITER = 100_000, KEY_BITS = 256;
    private static final SecureRandom RNG = new SecureRandom();
    private PasswordHasher() {}

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        return "pbkdf2$" + ITER + "$" + b64(salt) + "$" + b64(derive(password, salt, ITER));
    }

    public static boolean verify(String password, String stored) {
        try {
            String[] p = stored.split("\\$");
            if (p.length != 4 || !p[0].equals("pbkdf2")) return false;
            int it = Integer.parseInt(p[1]);
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, it)); 
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iter) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, iter, KEY_BITS)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }

    public static void main(String[] args) {
        String h = hash("Test@123");
        System.out.println(h);
        System.out.println("correct pw  -> " + verify("Test@123", h)); 
        System.out.println("wrong pw    -> " + verify("test@123", h)); 
        System.out.println("garbage     -> " + verify("x", "nonsense")); 
    }
}