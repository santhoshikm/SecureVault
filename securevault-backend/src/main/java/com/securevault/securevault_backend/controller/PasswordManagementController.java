package com.securevault.securevault_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping("/api/vault/password")
public class PasswordManagementController {

    private static final String LOWER        = "abcdefghijklmnopqrstuvwxyz";
    private static final String LOWER_NOAMB  = "abcdefghjkmnpqrstuvwxyz";    // no l, i, o
    private static final String UPPER        = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String UPPER_NOAMB  = "ABCDEFGHJKMNPQRSTUVWXYZ";    // no I, O
    private static final String DIGITS       = "0123456789";
    private static final String DIGITS_NOAMB = "23456789";                    // no 0, 1
    private static final String SYMBOLS      = "!@#$%^&*()_+-=[]{}|;:,.<>?";
    private static final String SYMBOLS_SAFE = "!@#$%&*_+-=";                 // shell-safe subset

    private static final SecureRandom RNG = new SecureRandom();

    // ── Generate password ─────────────────────────────────────────────────────

    /**
     * GET /api/vault/password/generate
     *
     * Params:
     *   length        (default 16, 4–128)
     *   uppercase     (default true)
     *   lowercase     (default true)
     *   numbers       (default true)
     *   symbols       (default true)
     *   excludeAmbiguous (default false) — removes l/1/I/O/0
     *   customSymbols (optional)         — override symbol set
     *   prefix        (optional)         — fixed prefix prepended to password
     *   minUppercase  (default 0)        — minimum guaranteed uppercase chars
     *   minNumbers    (default 0)        — minimum guaranteed number chars
     *   minSymbols    (default 0)        — minimum guaranteed symbol chars
     */
    @GetMapping("/generate")
    public ResponseEntity<Map<String, Object>> generatePassword(
            @RequestParam(defaultValue = "16")    int     length,
            @RequestParam(defaultValue = "true")  boolean uppercase,
            @RequestParam(defaultValue = "true")  boolean lowercase,
            @RequestParam(defaultValue = "true")  boolean numbers,
            @RequestParam(defaultValue = "true")  boolean symbols,
            @RequestParam(defaultValue = "false") boolean excludeAmbiguous,
            @RequestParam(defaultValue = "")      String  customSymbols,
            @RequestParam(defaultValue = "")      String  prefix,
            @RequestParam(defaultValue = "0")     int     minUppercase,
            @RequestParam(defaultValue = "0")     int     minNumbers,
            @RequestParam(defaultValue = "0")     int     minSymbols) {

        // Clamp length
        length = Math.max(4, Math.min(128, length));

        // Build charset segments
        String lowerSet   = excludeAmbiguous ? LOWER_NOAMB  : LOWER;
        String upperSet   = excludeAmbiguous ? UPPER_NOAMB  : UPPER;
        String digitSet   = excludeAmbiguous ? DIGITS_NOAMB : DIGITS;
        String symbolSet  = customSymbols.isBlank()
                            ? (excludeAmbiguous ? SYMBOLS_SAFE : SYMBOLS)
                            : customSymbols;

        StringBuilder pool = new StringBuilder();
        if (lowercase) pool.append(lowerSet);
        if (uppercase) pool.append(upperSet);
        if (numbers)   pool.append(digitSet);
        if (symbols)   pool.append(symbolSet);
        if (pool.isEmpty()) pool.append(lowerSet).append(upperSet).append(digitSet);

        String poolStr = pool.toString();
        int prefixLen  = prefix.length();
        int needed     = Math.max(0, length - prefixLen);

        // Guarantee minimums
        List<Character> chars = new ArrayList<>();
        for (int i = 0; i < minUppercase && uppercase; i++) chars.add(pickRandom(upperSet));
        for (int i = 0; i < minNumbers   && numbers;   i++) chars.add(pickRandom(digitSet));
        for (int i = 0; i < minSymbols   && symbols;   i++) chars.add(pickRandom(symbolSet));

        // Fill remainder
        while (chars.size() < needed) chars.add(pickRandom(poolStr));
        if (chars.size() > needed) chars = chars.subList(0, needed);

        // Shuffle
        Collections.shuffle(chars, RNG);

        String password = prefix + listToString(chars);

        // Strength analysis
        Map<String, Object> analysis = analyzePassword(password);

        // Build suggestions list
        List<String> suggestions = buildSuggestions(password, uppercase, lowercase, numbers, symbols, length);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("password",    password);
        response.put("length",      password.length());
        response.put("strength",    analysis);
        response.put("suggestions", suggestions);
        return ResponseEntity.ok(response);
    }

    // ── Bulk generation ───────────────────────────────────────────────────────

    /**
     * GET /api/vault/password/generate/bulk?count=5&length=20
     * Returns a list of generated passwords with strength scores.
     */
    @GetMapping("/generate/bulk")
    public ResponseEntity<List<Map<String, Object>>> generateBulk(
            @RequestParam(defaultValue = "5")    int count,
            @RequestParam(defaultValue = "20")   int length,
            @RequestParam(defaultValue = "true") boolean uppercase,
            @RequestParam(defaultValue = "true") boolean lowercase,
            @RequestParam(defaultValue = "true") boolean numbers,
            @RequestParam(defaultValue = "true") boolean symbols,
            @RequestParam(defaultValue = "false") boolean excludeAmbiguous) {

        count  = Math.max(1, Math.min(20, count));
        length = Math.max(4, Math.min(128, length));

        String lowerSet  = excludeAmbiguous ? LOWER_NOAMB  : LOWER;
        String upperSet  = excludeAmbiguous ? UPPER_NOAMB  : UPPER;
        String digitSet  = excludeAmbiguous ? DIGITS_NOAMB : DIGITS;
        String symbolSet = excludeAmbiguous ? SYMBOLS_SAFE : SYMBOLS;

        StringBuilder pool = new StringBuilder();
        if (lowercase) pool.append(lowerSet);
        if (uppercase) pool.append(upperSet);
        if (numbers)   pool.append(digitSet);
        if (symbols)   pool.append(symbolSet);
        if (pool.isEmpty()) pool.append(lowerSet).append(upperSet).append(digitSet);
        String poolStr = pool.toString();

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Character> chars = new ArrayList<>();
            if (uppercase) chars.add(pickRandom(upperSet));
            if (numbers)   chars.add(pickRandom(digitSet));
            if (symbols)   chars.add(pickRandom(symbolSet));
            while (chars.size() < length) chars.add(pickRandom(poolStr));
            Collections.shuffle(chars, RNG);
            String pw = listToString(chars);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("password", pw);
            item.put("strength", analyzePassword(pw));
            results.add(item);
        }
        return ResponseEntity.ok(results);
    }

    // ── Password health ───────────────────────────────────────────────────────

    /**
     * POST /api/vault/password/health
     * Body: { "password": "..." }
     */
    @PostMapping("/health")
    public ResponseEntity<Map<String, Object>> evaluatePasswordHealth(
            @RequestBody Map<String, String> request) {

        String password = request.getOrDefault("password", "");
        Map<String, Object> result = analyzePassword(password);
        result.put("suggestions", buildSuggestions(password, true, true, true, true, password.length()));
        return ResponseEntity.ok(result);
    }

    // ── Passphrase generation ─────────────────────────────────────────────────

    /**
     * GET /api/vault/password/passphrase?words=4&separator=-
     * Generates a memorable word-based passphrase.
     */
    @GetMapping("/passphrase")
    public ResponseEntity<Map<String, Object>> generatePassphrase(
            @RequestParam(defaultValue = "4")  int    words,
            @RequestParam(defaultValue = "-")  String separator,
            @RequestParam(defaultValue = "true") boolean capitalize,
            @RequestParam(defaultValue = "true") boolean addNumber) {

        words = Math.max(2, Math.min(10, words));

        // Simple wordlist (production: load from file)
        String[] wordList = {
            "apple","bridge","cloud","dragon","ember","forest","garden","harbor",
            "island","jungle","knight","lantern","meadow","nebula","ocean","palace",
            "quartz","river","silver","timber","umbrella","valley","winter","xenon",
            "yellow","zenith","anchor","beacon","castle","delta","eagle","falcon",
            "glacier","horizon","iris","jaguar","karma","lotus","marble","nimbus",
            "orbit","prism","quasar","rocket","sunset","tiger","ultra","violet",
            "wave","xray","yoga","zeal","amber","bronze","coral","dune","echo",
            "flame","grove","hollow","indigo","jade","kite","lemon","maple","neon"
        };

        StringBuilder phrase = new StringBuilder();
        for (int i = 0; i < words; i++) {
            String w = wordList[RNG.nextInt(wordList.length)];
            if (capitalize) w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            if (i > 0) phrase.append(separator);
            phrase.append(w);
        }
        if (addNumber) phrase.append(separator).append(RNG.nextInt(9000) + 1000);

        String passphrase = phrase.toString();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("passphrase", passphrase);
        response.put("length",     passphrase.length());
        response.put("strength",   analyzePassword(passphrase));
        return ResponseEntity.ok(response);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private char pickRandom(String set) {
        return set.charAt(RNG.nextInt(set.length()));
    }

    private String listToString(List<Character> list) {
        StringBuilder sb = new StringBuilder(list.size());
        for (char c : list) sb.append(c);
        return sb.toString();
    }

    /** Full password strength analysis */
    private Map<String, Object> analyzePassword(String pw) {
        int length   = pw.length();
        boolean hasUpper  = pw.matches(".*[A-Z].*");
        boolean hasLower  = pw.matches(".*[a-z].*");
        boolean hasDigit  = pw.matches(".*[0-9].*");
        boolean hasSymbol = pw.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*");
        boolean hasRepeat = pw.matches(".*(.)\\1{2,}.*");   // 3+ repeated chars
        boolean isSequential = containsSequence(pw);

        int score = 0;
        if (length >= 8)  score += 20;
        if (length >= 12) score += 15;
        if (length >= 16) score += 10;
        if (hasUpper)  score += 12;
        if (hasLower)  score += 12;
        if (hasDigit)  score += 12;
        if (hasSymbol) score += 15;
        if (hasRepeat)     score -= 10;
        if (isSequential)  score -= 10;
        score = Math.max(0, Math.min(100, score));

        String rating;
        if (score < 30)       rating = "WEAK";
        else if (score < 60)  rating = "MEDIUM";
        else if (score < 85)  rating = "STRONG";
        else                  rating = "VERY STRONG";

        // Estimated crack time (rough)
        String crackTime = estimateCrackTime(pw);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score",        score);
        result.put("rating",       rating);
        result.put("crackTime",    crackTime);
        result.put("length",       length);
        result.put("hasUppercase", hasUpper);
        result.put("hasLowercase", hasLower);
        result.put("hasNumbers",   hasDigit);
        result.put("hasSymbols",   hasSymbol);
        result.put("hasRepeats",   hasRepeat);
        result.put("isSequential", isSequential);
        return result;
    }

    private boolean containsSequence(String pw) {
        String lower = pw.toLowerCase();
        for (int i = 0; i < lower.length() - 2; i++) {
            if (lower.charAt(i+1) == lower.charAt(i) + 1 &&
                lower.charAt(i+2) == lower.charAt(i) + 2) return true;
        }
        return false;
    }

    private String estimateCrackTime(String pw) {
        // Charset size estimation
        int charsetSize = 0;
        if (pw.matches(".*[a-z].*")) charsetSize += 26;
        if (pw.matches(".*[A-Z].*")) charsetSize += 26;
        if (pw.matches(".*[0-9].*")) charsetSize += 10;
        if (pw.matches(".*[^a-zA-Z0-9].*")) charsetSize += 32;
        if (charsetSize == 0) charsetSize = 26;

        // 10 billion guesses/sec (modern GPU)
        double combinations = Math.pow(charsetSize, pw.length());
        double seconds      = combinations / 1e10;

        if (seconds < 1)         return "< 1 second";
        if (seconds < 60)        return (int)seconds + " seconds";
        if (seconds < 3600)      return (int)(seconds/60) + " minutes";
        if (seconds < 86400)     return (int)(seconds/3600) + " hours";
        if (seconds < 2592000)   return (int)(seconds/86400) + " days";
        if (seconds < 31536000)  return (int)(seconds/2592000) + " months";
        if (seconds < 3.15e9)    return (int)(seconds/31536000) + " years";
        return "centuries";
    }

    private List<String> buildSuggestions(String pw, boolean uppercase, boolean lowercase,
                                           boolean numbers, boolean symbols, int length) {
        List<String> tips = new ArrayList<>();
        if (pw.length() < 12)    tips.add("Use at least 12 characters for better security.");
        if (!pw.matches(".*[A-Z].*")) tips.add("Add uppercase letters (A–Z).");
        if (!pw.matches(".*[a-z].*")) tips.add("Add lowercase letters (a–z).");
        if (!pw.matches(".*[0-9].*")) tips.add("Add numbers (0–9).");
        if (!pw.matches(".*[^a-zA-Z0-9].*")) tips.add("Add special characters (!@#$ etc.).");
        if (pw.matches(".*(.)\\1{2,}.*")) tips.add("Avoid repeating the same character 3+ times.");
        if (containsSequence(pw)) tips.add("Avoid sequential characters (abc, 123).");
        if (tips.isEmpty()) tips.add("Great password! Keep it safe.");
        return tips;
    }
}
