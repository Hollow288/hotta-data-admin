package com.hollow.build.agent.alias.util;

/**
 * Levenshtein 编辑距离：把字符串 a 变成 b 需要的最少操作数（插入/删除/替换 各算 1）。
 *
 * <p>采用滚动数组的 O(n) 空间实现：只保留上一行 + 当前行。
 *
 * <p>用法示例：
 * <pre>
 *   EditDistance.between("洪莲", "红莲")   // 1
 *   EditDistance.between("赤峰", "赤风")   // 1
 *   EditDistance.between("洪莲", "红莲刃") // 2
 *   EditDistance.atMost("洪莲", "红莲", 1) // true
 * </pre>
 */
public final class EditDistance {

    private EditDistance() {}

    public static int between(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }

    /**
     * 长度差超过阈值的对，距离一定 &gt; threshold，直接短路。
     * 用在大规模扫描里能省掉大量 DP 计算。
     */
    public static boolean atMost(String a, String b, int threshold) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (Math.abs(a.length() - b.length()) > threshold) return false;
        return between(a, b) <= threshold;
    }
}
