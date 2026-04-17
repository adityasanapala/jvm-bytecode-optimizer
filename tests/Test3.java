// Scenario: Redundant Load Elimination — same instance field read multiple times
// with no intervening store. optimizer should cache field in a temp local.
// Expected output: result = 300000000, Correct: true

public class Test3 {

    static class Config {
        public int multiplier = 3;
        public int offset     = 0;
    }

    static int compute(Config cfg, int x) {
        // cfg.multiplier loaded twice with no store in between — RLE should eliminate 2nd load
        int a = cfg.multiplier * x;
        int b = cfg.multiplier * (x + 1); // redundant load of cfg.multiplier
        return a + b;
    }

    public static void main(String[] args) {
        Config cfg = new Config();
        long result = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10_000_000; i++) {
            result += compute(cfg, i % 10);
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Correct: " + (result == 300_000_030L)); // Expected: Correct: true
        System.out.println("Time(ms): " + elapsed);
    }
}
