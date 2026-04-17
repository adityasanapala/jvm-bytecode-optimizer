// Scenario: Method Inlining — small private getter methods inlined at call sites.
// Point.getX() and Point.getY() are tiny (1 stmt each); inlining eliminates call overhead.
// Expected output: sum = 1500000000, Correct: true

public class Test2 {

    static class Point {
        private int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
        // Small methods — ideal inline candidates
        public final int getX() { return x; }  // 1 stmt body
        public final int getY() { return y; }  // 1 stmt body
    }

    public static void main(String[] args) {
        Point p = new Point(10, 5);
        long sum = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100_000_000; i++) {
            sum += p.getX(); // Expected to be inlined
            sum += p.getY(); // Expected to be inlined
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Correct: " + (sum == 1_500_000_000L)); // Expected: Correct: true
        System.out.println("Time(ms): " + elapsed);
    }
}
