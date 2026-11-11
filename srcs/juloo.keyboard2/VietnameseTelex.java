package juloo.keyboard2;

import android.view.inputmethod.InputConnection;
import java.util.HashMap;
import java.util.Map;

public class VietnameseTelex {
    public static class Result {
        public final int backspaceCount;
        public final String committedText;

        public Result(int backspaceCount, String committedText) {
            this.backspaceCount = backspaceCount;
            this.committedText = committedText;
        }
    }

    private static final StringBuilder buffer = new StringBuilder();

    // 12 nguyên âm x 6 thanh: Ngang, Sắc, Huyền, Hỏi, Ngã, Nặng
    private static final char[][] VOWELS = {
        {'a', 'á', 'à', 'ả', 'ã', 'ạ'},
        {'ă', 'ắ', 'ằ', 'ẳ', 'ẵ', 'ặ'},
        {'â', 'ấ', 'ầ', 'ẩ', 'ẫ', 'ậ'},
        {'e', 'é', 'è', 'ẻ', 'ẽ', 'ẹ'},
        {'ê', 'ế', 'ề', 'ể', 'ễ', 'ệ'},
        {'i', 'í', 'ì', 'ỉ', 'ĩ', 'ị'},
        {'o', 'ó', 'ò', 'ỏ', 'õ', 'ọ'},
        {'ô', 'ố', 'ồ', 'ổ', 'ỗ', 'ộ'},
        {'ơ', 'ớ', 'ờ', 'ở', 'ỡ', 'ợ'},
        {'u', 'ú', 'ù', 'ủ', 'ũ', 'ụ'},
        {'ư', 'ứ', 'ừ', 'ử', 'ữ', 'ự'},
        {'y', 'ý', 'ỳ', 'ỷ', 'ỹ', 'ỵ'}
    };

    private static final Map<Character, int[]> CHAR_MAP = new HashMap<>();

    static {
        for (int b = 0; b < VOWELS.length; b++) {
            for (int t = 0; t < 6; t++) {
                char lower = VOWELS[b][t];
                char upper = Character.toUpperCase(lower);
                CHAR_MAP.put(lower, new int[]{b, t, 0});
                CHAR_MAP.put(upper, new int[]{b, t, 1});
            }
        }
    }

    public static void reset() {
        buffer.setLength(0);
    }

    public static void handleBackspace() {
        if (buffer.length() > 0) {
            buffer.deleteCharAt(buffer.length() - 1);
        }
    }

    public static Result processChar(char c, InputConnection ic) {
        if (ic == null) return null;

        if (!Character.isLetter(c)) {
            reset();
            return null;
        }

        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if ((before == null || before.length() == 0) && buffer.length() > 0) {
            buffer.setLength(0);
        }

        String oldWord = buffer.toString();
        String newWord = applyTelex(oldWord, c);

        if (newWord.equals(oldWord + c)) {
            buffer.append(c);
            if (buffer.length() > 15) buffer.delete(0, buffer.length() - 15);
            return null; // Gửi phím bình thường
        } else {
            buffer.setLength(0);
            buffer.append(newWord);
            int common = 0;
            int minLen = Math.min(oldWord.length(), newWord.length());
            while (common < minLen && oldWord.charAt(common) == newWord.charAt(common)) {
                common++;
            }
            int backspaceCount = oldWord.length() - common;
            String committedText = newWord.substring(common);
            return new Result(backspaceCount, committedText);
        }
    }

    private static String applyTelex(String word, char key) {
        char lk = Character.toLowerCase(key);
        boolean isUpperKey = Character.isUpperCase(key);

        // Khi bắt đầu từ mới: gõ 'w' hoặc 'W' ra ngay 'ư' hoặc 'Ư'
        if (word.isEmpty()) {
            if (lk == 'w') return isUpperKey ? "Ư" : "ư";
            return String.valueOf(key);
        }

        char[] chars = word.toCharArray();
        int len = chars.length;

        // 1. Xử lý 'd' -> 'đ' (hoàn tác 'đ' + 'd' -> 'dd')
        if (lk == 'd') {
            for (int i = len - 1; i >= 0; i--) {
                if (chars[i] == 'đ') {
                    chars[i] = 'd';
                    return new String(chars) + key;
                }
                if (chars[i] == 'Đ') {
                    chars[i] = 'D';
                    return new String(chars) + key;
                }
                if (chars[i] == 'd' && (i == len - 1 || i == 0)) {
                    chars[i] = 'đ';
                    return repositionTone(new String(chars));
                }
                if (chars[i] == 'D' && (i == len - 1 || i == 0)) {
                    chars[i] = 'Đ';
                    return repositionTone(new String(chars));
                }
            }
            return word + key;
        }

        // 2. Xử lý mũ nguyên âm: a+a->â, e+e->ê, o+o->ô
        if (lk == 'a' || lk == 'e' || lk == 'o') {
            int targetBase = (lk == 'a') ? 0 : (lk == 'e' ? 3 : 6);
            int circumflexBase = targetBase + 2;

            for (int i = len - 1; i >= 0; i--) {
                int[] info = CHAR_MAP.get(chars[i]);
                if (info != null) {
                    if (info[0] == circumflexBase) {
                        chars[i] = makeChar(targetBase, info[1], info[2]);
                        return new String(chars) + key;
                    }
                    if (info[0] == targetBase) {
                        chars[i] = makeChar(circumflexBase, info[1], info[2]);
                        return repositionTone(new String(chars));
                    }
                }
            }
            return word + key;
        }

        // 3. Xử lý phím 'w' (ư độc lập, ươ, ư, ơ, ă)
        if (lk == 'w') {
            // Hoàn tác nếu ký tự cuối là 'ư'/'Ư' vừa sinh ra từ 'w' đứng độc lập hoặc sau phụ âm
            char lastChar = chars[len - 1];
            int[] lastInfo = CHAR_MAP.get(lastChar);
            if (lastInfo != null && lastInfo[0] == 10 && lastInfo[1] == 0) {
                boolean hasOtherVowel = false;
                for (int i = 0; i < len - 1; i++) {
                    if (CHAR_MAP.containsKey(chars[i])) {
                        hasOtherVowel = true;
                        break;
                    }
                }
                if (!hasOtherVowel) {
                    chars[len - 1] = (lastInfo[2] == 1) ? 'W' : 'w';
                    return new String(chars);
                }
            }

            // Tổ hợp uo / uơ / ưo -> ươ
            for (int i = len - 1; i >= 1; i--) {
                int[] info2 = CHAR_MAP.get(chars[i]);
                int[] info1 = CHAR_MAP.get(chars[i - 1]);
                if (info1 != null && info2 != null) {
                    boolean isU = (info1[0] == 9 || info1[0] == 10);
                    boolean isO = (info2[0] == 6 || info2[0] == 8);
                    if (isU && isO) {
                        if (info1[0] == 10 && info2[0] == 8) {
                            chars[i - 1] = makeChar(9, info1[1], info1[2]);
                            chars[i] = makeChar(6, info2[1], info2[2]);
                            return new String(chars) + key;
                        }
                        chars[i - 1] = makeChar(10, info1[1], info1[2]);
                        chars[i] = makeChar(8, info2[1], info2[2]);
                        return repositionTone(new String(chars));
                    }
                }
            }

            // Biến đổi a->ă, o->ơ, u->ư (hoặc hoàn tác)
            for (int i = len - 1; i >= 0; i--) {
                int[] info = CHAR_MAP.get(chars[i]);
                if (info != null) {
                    if (info[0] == 1) {
                        chars[i] = makeChar(0, info[1], info[2]);
                        return new String(chars) + key;
                    }
                    if (info[0] == 8) {
                        chars[i] = makeChar(6, info[1], info[2]);
                        return new String(chars) + key;
                    }
                    if (info[0] == 10) {
                        chars[i] = makeChar(9, info[1], info[2]);
                        return new String(chars) + key;
                    }
                    if (info[0] == 0) {
                        chars[i] = makeChar(1, info[1], info[2]);
                        return repositionTone(new String(chars));
                    }
                    if (info[0] == 6) {
                        chars[i] = makeChar(8, info[1], info[2]);
                        return repositionTone(new String(chars));
                    }
                    if (info[0] == 9) {
                        chars[i] = makeChar(10, info[1], info[2]);
                        return repositionTone(new String(chars));
                    }
                }
            }

            // Nếu trước đó chỉ là phụ âm (như "nh", "th", "tr", "ch", "c"...), gõ 'w' thêm thẳng 'ư'
            return word + (isUpperKey ? 'Ư' : 'ư');
        }

        // 4. Xử lý dấu thanh: s, f, r, x, j, z
        int tone = -1;
        if (lk == 's') tone = 1;
        else if (lk == 'f') tone = 2;
        else if (lk == 'r') tone = 3;
        else if (lk == 'x') tone = 4;
        else if (lk == 'j') tone = 5;
        else if (lk == 'z') tone = 0;

        if (tone != -1) {
            int pos = findVowelPositionForTone(chars);
            if (pos == -1) return word + key;

            int currentTone = 0;
            int currentTonePos = -1;
            for (int i = 0; i < len; i++) {
                int[] info = CHAR_MAP.get(chars[i]);
                if (info != null && info[1] != 0) {
                    currentTone = info[1];
                    currentTonePos = i;
                    break;
                }
            }

            if (tone == 0) {
                if (currentTone == 0) return word + key;
                int[] info = CHAR_MAP.get(chars[currentTonePos]);
                chars[currentTonePos] = makeChar(info[0], 0, info[2]);
                return new String(chars);
            }

            if (currentTone == tone) {
                int[] info = CHAR_MAP.get(chars[currentTonePos]);
                chars[currentTonePos] = makeChar(info[0], 0, info[2]);
                return new String(chars) + key;
            }

            if (currentTonePos != -1) {
                int[] info = CHAR_MAP.get(chars[currentTonePos]);
                chars[currentTonePos] = makeChar(info[0], 0, info[2]);
            }

            int[] targetInfo = CHAR_MAP.get(chars[pos]);
            chars[pos] = makeChar(targetInfo[0], tone, targetInfo[2]);
            return new String(chars);
        }

        // 5. Khi gõ thêm nguyên âm hoặc phụ âm cuối, tự động dịch chuyển dấu thanh về đúng vị trí
        String appended = word + key;
        if (hasTone(chars)) {
            return repositionTone(appended);
        }
        return appended;
    }

    private static boolean hasTone(char[] chars) {
        for (char c : chars) {
            int[] info = CHAR_MAP.get(c);
            if (info != null && info[1] != 0) return true;
        }
        return false;
    }

    private static String repositionTone(String word) {
        char[] chars = word.toCharArray();
        int tone = 0;
        for (int i = 0; i < chars.length; i++) {
            int[] info = CHAR_MAP.get(chars[i]);
            if (info != null && info[1] != 0) {
                tone = info[1];
                chars[i] = makeChar(info[0], 0, info[2]);
            }
        }
        if (tone == 0) return word;
        int pos = findVowelPositionForTone(chars);
        if (pos != -1) {
            int[] info = CHAR_MAP.get(chars[pos]);
            chars[pos] = makeChar(info[0], tone, info[2]);
        }
        return new String(chars);
    }

    private static int findVowelPositionForTone(char[] chars) {
        int len = chars.length;
        int firstV = -1, lastV = -1;
        int vCount = 0;

        for (int i = 0; i < len; i++) {
            if (CHAR_MAP.containsKey(chars[i])) {
                if (i == 1 && vCount == 0) {
                    char prev = Character.toLowerCase(chars[0]);
                    char curr = Character.toLowerCase(chars[1]);
                    if ((prev == 'q' && curr == 'u') || (prev == 'g' && curr == 'i')) {
                        if (i + 1 < len && CHAR_MAP.containsKey(chars[i + 1])) {
                            continue;
                        }
                    }
                }
                if (firstV == -1) firstV = i;
                lastV = i;
                vCount++;
            }
        }

        if (firstV == -1) return -1;
        if (vCount == 1) return firstV;

        for (int i = firstV; i < lastV; i++) {
            int[] info1 = CHAR_MAP.get(chars[i]);
            int[] info2 = CHAR_MAP.get(chars[i + 1]);
            if (info1 != null && info2 != null && info1[0] == 10 && info2[0] == 8) {
                return i + 1;
            }
        }

        for (int i = firstV; i <= lastV; i++) {
            int[] info = CHAR_MAP.get(chars[i]);
            if (info != null) {
                int b = info[0];
                if (b == 1 || b == 2 || b == 4 || b == 7 || b == 8 || b == 10) {
                    return i;
                }
            }
        }

        boolean hasEndingConsonant = (lastV < len - 1);
        if (vCount == 2) {
            return hasEndingConsonant ? lastV : firstV;
        }
        return firstV + 1;
    }

    private static char makeChar(int base, int tone, int isUpper) {
        char c = VOWELS[base][tone];
        return isUpper == 1 ? Character.toUpperCase(c) : c;
    }
}