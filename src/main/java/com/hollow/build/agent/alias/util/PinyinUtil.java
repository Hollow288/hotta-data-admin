package com.hollow.build.agent.alias.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 拼音工具：把中文字符串转成"音节序列字符串"（用空格分隔），并提供按音节比对的辅助方法。
 *
 * <p>关键点是：直接用 {@code String.contains} 比对拼音串会出现"半音节误匹配"
 *（例如 "lin" 会命中 "ling han" 中的前缀）。这里通过两端补空格再做 contains，
 * 强制音节边界对齐：
 * <pre>
 *   " hong lian " ⊂ " da hong lian "  → true  （整音节命中）
 *   " hong lia "  ⊂ " da hong lian "  → false （半音节）
 * </pre>
 *
 * <p>多音字处理：pinyin4j 会返回所有读音，这里只取第一个（最常用的那个）。对游戏物品名
 * 绝大多数无碍；极个别多音名（如 "重蕊"）即便拼音偏了，编辑距离那一层也能兜住。
 */
public final class PinyinUtil {

    private static final HanyuPinyinOutputFormat FORMAT;

    static {
        FORMAT = new HanyuPinyinOutputFormat();
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    private PinyinUtil() {}

    /** 把字符串转成 " hong lian " 这种两端带空格、小写的音节串，便于按音节做子串匹配。 */
    public static String paddedSyllables(String s) {
        if (s == null || s.isBlank()) return " ";
        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String syllable = toSyllable(c);
            if (!syllable.isEmpty()) {
                sb.append(syllable).append(' ');
            }
        }
        return sb.toString();
    }

    private static String toSyllable(char c) {
        try {
            String[] pys = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
            if (pys != null && pys.length > 0) {
                // 多音字取第一个读音。
                return pys[0];
            }
        } catch (BadHanyuPinyinOutputFormatCombination ignored) {
            // 当前 FORMAT 是 LOWERCASE + WITHOUT_TONE，不会触发；保底走下面分支。
        }
        // 非汉字：字母/数字保留（已小写），其它跳过避免污染音节串。
        if (Character.isLetterOrDigit(c)) {
            return String.valueOf(Character.toLowerCase(c));
        }
        return "";
    }

    /** 整串拼音完全相等（按音节）。 */
    public static boolean syllablesEqual(String a, String b) {
        return paddedSyllables(a).equals(paddedSyllables(b));
    }

    /** 任意一个的拼音是另一个的"音节级子串"（包括相等）。 */
    public static boolean syllablesContain(String a, String b) {
        String pa = paddedSyllables(a);
        String pb = paddedSyllables(b);
        return pa.contains(pb) || pb.contains(pa);
    }
}
