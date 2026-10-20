package ru.net.terracraft.chat.util;

public final class LevenshteinUtil {

    private LevenshteinUtil() {}

    /**
     * Считает схожесть двух строк: 1.0 = идентичны, 0.0 = совсем не похожи.
     */
    public static double similarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equalsIgnoreCase(b)) return 1.0;

        String s1 = a.toLowerCase(java.util.Locale.ROOT);
        String s2 = b.toLowerCase(java.util.Locale.ROOT);

        int distance = distance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());

        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * Классический Levenshtein distance с двумя массивами.
     */
    public static int distance(String s1, String s2) {
        int n = s1.length();
        int m = s2.length();

        if (n == 0) return m;
        if (m == 0) return n;

        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int i = 0; i <= n; i++) prev[i] = i;

        for (int j = 1; j <= m; j++) {
            curr[0] = j;
            for (int i = 1; i <= n; i++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                curr[i] = Math.min(
                        Math.min(curr[i - 1] + 1, prev[i] + 1),
                        prev[i - 1] + cost
                );
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[n];
    }
}