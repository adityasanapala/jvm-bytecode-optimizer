// Scenario: Redundant Load Elimination — repeated reads of a static field.
// MathConstants.PI is read multiple times; RLE should load it once per basic block.
// Expected output: Correct: true

public class Test5 {

    static class MathConstants {
        static final double PI = 3.141592653589793;
        static final double E  = 2.718281828459045;
    }

    static double circlePerimeter(double r) {
        // MathConstants.PI loaded three times with no intervening store — RLE eliminates 2
        double c = 2 * MathConstants.PI * r;
        double approx = MathConstants.PI * r * r; // redundant static load
        return c + approx * 0;                    // just use c
    }

    public static void main(String[] args) {
        double result = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < 50_000_000; i++) {
            result += circlePerimeter(i % 100);
        }
        long elapsed = System.currentTimeMillis() - start;
        // Just checking it's positive and non-zero
        System.out.println("Correct: " + (result > 0)); // Expected: Correct: true
        System.out.println("Time(ms): " + elapsed);
    }
}
