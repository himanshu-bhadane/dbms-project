package dental.db;

import java.util.List;

/** Generic tabular result used by reports / joined searches. */
public record Table(List<String> cols, List<List<String>> rows) {
    private static final int MAX_W = 38;

    private static String cut(String s) {
        return s.length() > MAX_W ? s.substring(0, MAX_W - 1) + "…" : s;
    }

    public void print() {
        if (rows.isEmpty()) { System.out.println("(no rows)"); return; }
        int n = cols.size();
        int[] w = new int[n];
        for (int i = 0; i < n; i++) {
            w[i] = cols.get(i).length();
            for (List<String> r : rows) w[i] = Math.max(w[i], cut(r.get(i)).length());
        }
        line(cols, w);
        StringBuilder sep = new StringBuilder();
        for (int i = 0; i < n; i++) sep.append("-".repeat(w[i])).append(i < n - 1 ? "-+-" : "");
        System.out.println(sep);
        for (List<String> r : rows) line(r, w);
        System.out.println(rows.size() + " row(s)");
    }

    private static void line(List<String> r, int[] w) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            sb.append(String.format("%-" + w[i] + "s", cut(r.get(i))));
            if (i < w.length - 1) sb.append(" | ");
        }
        System.out.println(sb);
    }
}
