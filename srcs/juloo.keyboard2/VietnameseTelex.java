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

    // Chỉ số:
    // 0: a, 1: ă, 2: â, 3: e, 4: ê, 5: i, 6: o, 7: ô, 8: ơ, 9: u, 10: ư, 11: y
    private static final char[][] VOWELS = {
        {'a', 'á', 'à', 'ả', 'ã', 'ạ'}, // 0
        {'ă', 'ắ', 'ằ', 'ẳ', 'ẵ', 'ặ'}, // 1
        {'â', 'ấ', 'ầ', 'ẩ', 'ẫ', 'ậ'}, // 2
        {'e', 'é', 'è', 'ẻ', 'ẽ', 'ẹ'}, // 3
        {'ê', 'ế', 'ề', 'ể', 'ễ', 'ệ'}, // 4
        {'i', 'í', 'ì', 'ỉ', 'ĩ', 'ị'}, // 5
        {'o', 'ó', 'ò', 'ỏ', 'õ', 'ọ'}, // 6
        {'ô', 'ố', 'ồ', 'ổ', 'ỗ', 'ộ'}, // 7
        {'ơ', 'ớ', 'ờ', 'ở', 'ỡ', 'ợ'}, // 8
        {'u', 'ú', 'ù', 'ủ', 'ũ', 'ụ'}, // 9
        {'ư', 'ứ', 'ừ', 'ử', 'ữ', 'ự'}, // 10
        {'y', 'ý', 'ỳ', 'ỷ', 'ỹ', 'ỵ'}  // 11
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

    private static boolean isBracketShortcut(char c) {
        return c == '[' || c == ']' || c == '{' || c == '}';
    }

    public static Result processChar(char c, InputConnection ic) {
        if (ic == null) return null;

        // Cho phép chữ cái và các phím gõ nhanh [ ] { }
        if (!Character.isLetter(c) && !isBracketShortcut(c)) {
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
            return null;
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
        // 0. Xử lý phím gõ nhanh '[' -> 'ư', ']' -> 'ơ' (và '{' -> 'Ư', '}' -> 'Ơ')
        if (isBracketShortcut(key)) {
            char mappedVowel;
            int isUpper = (key == '{' || key == '}') ? 1 : 0;
            int baseIdx = (key == '[' || key == '{') ? 10 : 8; // 10 = ư, 8 = ơ
            mappedVowel = makeChar(baseIdx, 0, isUpper);

            if (!word.isEmpty()) {
                char last = word.charAt(word.length() - 1);
                int[] lastInfo = CHAR_MAP.get(last);
                // Hoàn tác nếu gõ 2 lần liên tiếp (ví dụ: [[ -> [)
                if (lastInfo != null && lastInfo[0] == baseIdx && lastInfo[1] == 0) {
                    return word.substring(0, word.length() - 1) + key;
                }
            }
            String nextWord = word + mappedVowel;
            nextWord = normalizeUOW(nextWord);
            return repositionTone(nextWord);
        }

        char lk = Character.toLowerCase(key);
        boolean isUpperKey = Character.isUpperCase(key);

        // Khi bắt đầu từ mới: gõ 'w' hoặc 'W' đơn lẻ tự động chuyển thành 'ư' hoặc 'Ư'
        if (word.isEmpty()) {
            if (lk == 'w') return isUpperKey ? "Ư" : "ư";
            return String.valueOf(key);
        }

        char[] chars = word.toCharArray();
        int len = chars.length;

        // 1. Xử lý 'dd' -> 'đ' (và hoàn tác 'đ' + 'd' -> 'dd')
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

        // 2. Xử lý dấu mũ: aa -> â (0->2), ee -> ê (3->4), oo -> ô (6->7)
        if (lk == 'a' || lk == 'e' || lk == 'o') {
            int targetBase = (lk == 'a') ? 0 : (lk == 'e' ? 3 : 6);
            int circumflexBase = (lk == 'a') ? 2 : (lk == 'e' ? 4 : 7);

            for (int i = len - 1; i >= 0; i--) {
                int[] info = CHAR_MAP.get(chars[i]);
                if (info != null) {
                    if (info[0] == circumflexBase) {
                        // Hoàn tác nếu gõ lần 3: â+a -> aa, ê+e -> ee, ô+o -> oo
                        chars[i] = makeChar(targetBase, info[1], info[2]);
                        return new String(chars) + key;
                    }
                    if (info[0] == targetBase) {
                        // Giữ nguyên trạng thái in hoa (info[2]) của nguyên âm gốc
                        chars[i] = makeChar(circumflexBase, info[1], info[2]);
                        return repositionTone(new String(chars));
                    }
                }
            }
            String appended = word + key;
            return hasTone(chars) ? repositionTone(appended) : appended;
        }

        // 3. Xử lý phím 'w': dấu trăng (ă), dấu móc (ư, ơ), và chữ 'w' đơn lẻ -> 'ư'
        if (lk == 'w') {
            // 3a. Hoàn tác nếu ký tự ngay trước là 'ư'/'Ư' vừa sinh ra từ 'w' đơn lẻ
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
                // Nếu chỉ có một mình 'ư' ở đầu từ (do gõ 'w'), gõ thêm 'w' sẽ trả về 'w'
                if (!hasOtherVowel && len == 1) {
                    chars[len - 1] = (lastInfo[2] == 1) ? 'W' : 'w';
                    return new String(chars);
                }
            }

            // 3b. Kiểm tra cụm nguyên âm đôi uo / ưo / uơ / ươ (hỗ trợ cả dduwowngf = đường và duongw = đương)
            for (int i = len - 1; i >= 1; i--) {
                int[] info2 = CHAR_MAP.get(chars[i]);
                int[] info1 = CHAR_MAP.get(chars[i - 1]);
                if (info1 != null && info2 != null) {
                    boolean isU = (info1[0] == 9 || info1[0] == 10);
                    boolean isO = (info2[0] == 6 || info2[0] == 8);
                    if (isU && isO) {
                        // Nếu đã là 'ươ' rồi mà gõ thêm 'w' -> hoàn tác về 'uow'
                        if (info1[0] == 10 && info2[0] == 8) {
                            chars[i - 1] = makeChar(9, info1[1], info1[2]);
                            chars[i] = makeChar(6, info2[1], info2[2]);
                            return new String(chars) + key;
                        }
                        // Nếu có phụ âm cuối (như "duong" + "w") hoặc là "ưo" (như "dduwo" + "w" trong "dduwowngf") -> chuyển cả cặp thành "ươ"
                        boolean hasEnding = (i < len - 1);
                        if (hasEnding || info1[0] == 10) {
                            chars[i - 1] = makeChar(10, info1[1], info1[2]);
                            chars[i] = makeChar(8, info2[1], info2[2]);
                            return repositionTone(new String(chars));
                        } else {
                            // Nếu đang gõ mở "huo" + "w" -> ưu tiên chuyển 'o' thành 'ơ' ("huơ"), nếu sau đó gõ thêm phụ âm sẽ tự chuẩn hóa thành "hươ..."
                            chars[i] = makeChar(8, info2[1], info2[2]);
                            return repositionTone(new String(chars));
                        }
                    }
                }
            }

            // 3c. Duyệt từ cuối lên để biến đổi a -> ă, o -> ơ, u -> ư (hoặc hoàn tác)
            for (int i = len - 1; i >= 0; i--) {
                int[] info = CHAR_MAP.get(chars[i]);
                if (info != null) {
                    // Bỏ qua chữ 'u' trong cụm 'qu' nếu phía sau còn nguyên âm khác (ví dụ: "qua" + "w" -> "quă")
                    if (i == 1 && info[0] == 9 && Character.toLowerCase(chars[0]) == 'q' && i + 1 < len && CHAR_MAP.containsKey(chars[i + 1])) {
                        continue;
                    }
                    if (info[0] == 1) { // ă + w -> aw
                        chars[i] = makeChar(0, info[1], info[2]);
                        return new String(chars) + key;
                    }
                    if (info[0] == 8) { // ơ + w -> ow
                        chars[i] = makeChar(6, info[1], info[2]);
                        return new String(chars) + key;
                    }
                    if (info[0] == 10) { // ư + w -> uw
                        chars[i] = makeChar(9, info[1], info[2]);
                        return new String(chars) + key;
                    }
                    if (info[0] == 0) { // a + w -> ă
                        chars[i] = makeChar(1, info[1], info[2]);
                        return repositionTone(new String(chars));
                    }
                    if (info[0] == 6) { // o + w -> ơ
                        chars[i] = makeChar(8, info[1], info[2]);
                        return repositionTone(new String(chars));
                    }
                    if (info[0] == 9) { // u + w -> ư
                        chars[i] = makeChar(10, info[1], info[2]);
                        return repositionTone(new String(chars));
                    }
                }
            }

            // 3d. Nếu từ chỉ mới có phụ âm đầu (như "nh", "th", "tr", "ch", "dd"...), gõ 'w' tự chuyển thành 'ư'
            return word + (isUpperKey ? 'Ư' : 'ư');
        }

        // 4. Xử lý các phím dấu thanh: s (Sắc), f (Huyền), r (Hỏi), x (Ngã), j (Nặng), z (Xóa dấu)
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

            // Phím z: Xóa dấu thanh đã đặt (ví dụ: toansz = toan)
            if (tone == 0) {
                if (currentTone == 0) return word + key;
                int[] info = CHAR_MAP.get(chars[currentTonePos]);
                chars[currentTonePos] = makeChar(info[0], 0, info[2]);
                return new String(chars);
            }

            // Nếu gõ lặp lại chính dấu đó -> hoàn tác dấu và trả lại ký tự phím (ví dụ: toanss = toans)
            if (currentTone == tone) {
                int[] info = CHAR_MAP.get(chars[currentTonePos]);
                chars[currentTonePos] = makeChar(info[0], 0, info[2]);
                return new String(chars) + key;
            }

            // Nếu gõ dấu mới đè lên dấu cũ (ví dụ: toanfs = toán) -> xóa dấu cũ trước
            if (currentTonePos != -1) {
                int[] info = CHAR_MAP.get(chars[currentTonePos]);
                chars[currentTonePos] = makeChar(info[0], 0, info[2]);
            }

            // Đặt dấu mới vào đúng nguyên âm (giữ nguyên chữ hoa/thường của nguyên âm đó)
            int[] targetInfo = CHAR_MAP.get(chars[pos]);
            chars[pos] = makeChar(targetInfo[0], tone, targetInfo[2]);
            return new String(chars);
        }

        // 5. Khi gõ thêm nguyên âm hoặc phụ âm cuối: chuẩn hóa cụm 'ươ' (nếu có) và tự động đặt lại vị trí dấu
        String appended = normalizeUOW(word + key);
        if (hasTone(appended.toCharArray())) {
            return repositionTone(appended);
        }
        return appended;
    }

    // Tự động chuyển "ưo" + phụ âm cuối hoặc "uơ" + phụ âm cuối thành "ươ" (ví dụ: dduwong -> đương, thuơng -> thương)
    private static String normalizeUOW(String word) {
        char[] chars = word.toCharArray();
        int len = chars.length;
        for (int i = 0; i < len - 1; i++) {
            int[] info1 = CHAR_MAP.get(chars[i]);
            int[] info2 = CHAR_MAP.get(chars[i + 1]);
            if (info1 != null && info2 != null) {
                boolean hasEnding = (i + 2 < len);
                // Nếu gõ [ + ] (ư + ơ) hoặc u + ] (u + ơ) có phụ âm cuối, hoặc ư + o có phụ âm cuối/nguyên âm i,u
                if ((info1[0] == 9 && info2[0] == 8 && hasEnding) ||
                    (info1[0] == 10 && info2[0] == 6 && hasEnding)) {
                    // Ngoại lệ: "quơ" không biến thành "qươ"
                    if (i == 1 && Character.toLowerCase(chars[0]) == 'q') continue;
                    chars[i] = makeChar(10, info1[1], info1[2]);
                    chars[i + 1] = makeChar(8, info2[1], info2[2]);
                }
            }
        }
        return new String(chars);
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
                // Bỏ qua 'u' trong 'qu' và 'i' trong 'gi' nếu theo sau còn nguyên âm khác
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

        // Ưu tiên 1: Nếu có cụm 'ươ', luôn đặt dấu vào 'ơ' (ví dụ: đường, trường, hướng)
        for (int i = firstV; i < lastV; i++) {
            int[] info1 = CHAR_MAP.get(chars[i]);
            int[] info2 = CHAR_MAP.get(chars[i + 1]);
            if (info1 != null && info2 != null && info1[0] == 10 && info2[0] == 8) {
                return i + 1;
            }
        }

        // Ưu tiên 2: Nếu có nguyên âm mang dấu mũ/móc/trăng (ă, â, ê, ô, ơ, ư), đặt dấu vào đúng nguyên âm đó (ví dụ: tiếng Việt)
        for (int i = firstV; i <= lastV; i++) {
            int[] info = CHAR_MAP.get(chars[i]);
            if (info != null) {
                int b = info[0];
                if (b == 1 || b == 2 || b == 4 || b == 7 || b == 8 || b == 10) {
                    return i;
                }
            }
        }

        // Ưu tiên 3: Nếu có phụ âm cuối (như hoàng, toán) hoặc có 3 nguyên âm -> đặt ở nguyên âm thứ 2; ngược lại đặt ở nguyên âm đầu
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