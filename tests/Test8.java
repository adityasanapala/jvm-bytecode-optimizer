// Scenario: Redundant Load Elimination — NEGATIVE case for RLE correctness.
// The field IS modified between reads, so RLE must NOT eliminate the second load.
// This tests soundness: output must remain correct after optimization.
// Expected output: Correct: true (result must equal 200000)

public class Test8 {

    static class Counter {
        int value = 0;
    }

    static int process(Counter c, int x) {
        int a = c.value;       // first load
        c.value = a + x;       // STORE — kills availability of c.value
        int b = c.value;       // second load — NOT redundant, must reload
        return a + b;
    }

    public static void main(String[] args) {
        Counter c = new Counter();
        long result = 0;
        for (int i = 0; i < 100_000; i++) {
            result += process(c, 1);
            c.value = 0; // reset
        }
        // Each call: a=0, store value=1, b=1, return 1. result = 100000
        System.out.println("Correct: " + (result == 100_000L)); // Expected: Correct: true
    }
}
