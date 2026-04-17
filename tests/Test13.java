// Scenario: Dead Field Elimination (intra-procedural) — a field is stored twice
// with no intervening read; the first store is dead and removed.
// Expected output: Correct: true

public class Test13 {

    static class Buffer {
        int capacity;
        int size;

        Buffer(int cap) {
            this.capacity = cap;
            this.size     = -1;  // dead store — immediately overwritten below
            this.size     = 0;   // this is the live store
        }

        void add() {
            if (size < capacity) size++;
        }

        int getSize() { return size; }
    }

    public static void main(String[] args) {
        long total = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < 2_000_000; i++) {
            Buffer b = new Buffer(10);
            for (int j = 0; j < 10; j++) b.add();
            total += b.getSize();
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Correct: " + (total == 2_000_000L * 10)); // Expected: Correct: true
        System.out.println("Time(ms): " + elapsed);
    }
}
