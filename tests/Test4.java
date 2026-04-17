// Scenario: Chained Optimizations — monomorphization exposes inlining opportunities.
// After devirtualizing Printer.print(), the static wrapper is small enough to inline.
// Expected output: Correct: true, with measurable speedup.

public class Test4 {

    interface Printer {
        void print(int x);
    }

    static class ConsolePrinter implements Printer {
        private long acc = 0;
        @Override
        public void print(int x) { acc += x; } // small body → inlineable after mono
        public long getAcc() { return acc; }
    }

    public static void main(String[] args) {
        ConsolePrinter cp = new ConsolePrinter();
        Printer p = cp; // only one concrete type
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10_000_000; i++) {
            p.print(i); // interface call → monomorphized → then inlined
        }
        long elapsed = System.currentTimeMillis() - start;
        long expected = (long) 10_000_000 * 9_999_999 / 2;
        System.out.println("Correct: " + (cp.getAcc() == expected)); // Expected: Correct: true
        System.out.println("Time(ms): " + elapsed);
    }
}
