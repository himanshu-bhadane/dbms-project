package dental.auth;

/** Delivery channel for reset codes. Dev stand-in: prints to the server console. Swap the body for SMS/email later. */
public final class Notifier {
    private Notifier() {}

    public static void send(String username, String code) {
        System.out.println("[RESET CODE] user=" + username + "  code=" + code + "  (valid 15 min)");
    }
}
