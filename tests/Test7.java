// Scenario: Method Inlining — callee returns a value used in an arithmetic expression.
// clamp() is a small, final method; its return value feeds directly into a sum.
// Expected output: Correct: true

public class Test7 {

    static class Util {
        static int clamp(int val, int lo, int hi) { // 1 stmt — ideal inline target
            return Math.min(Math.max(val, lo), hi);
        }
    }

    public static void main(String[] args) {
        long sum = 0;
        long start = System.currentTimeMillis();
        for (int i = -5_000_000; i < 5_000_000; i++) {
            sum += Util.clamp(i, 0, 100); // static call → inlineable
        }
        long elapsed = System.currentTimeMillis() - start;
        // sum of clamp(i,0,100) for i in [-5M, 5M)
        // For i<0: contribute 0 (5M values). For i in [0,100]: sum = 0+1+...+100 = 5050 (101 values)
        // For i in [101, 5M): contribute 100 each => 4_999_899 * 100
        long expected = 5050L + 4_999_899L * 100L;
        System.out.println("Correct: " + (sum == expected)); // Expected: Correct: true
        System.out.println("Time(ms): " + elapsed);
    }
}
