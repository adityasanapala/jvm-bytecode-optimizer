// Scenario: Dead Field Elimination — 'debugLabel' field written in constructor
// but never read anywhere in the program. The store is eliminated as dead.
// Expected output: Correct: true (behaviour unchanged, store simply removed)

public class Test12 {

    static class Packet {
        int    payload;
        String debugLabel; // written in ctor, NEVER read → dead field store

        Packet(int p, String label) {
            this.payload    = p;
            this.debugLabel = label; // dead store — should be eliminated
        }

        int getPayload() { return payload; }
    }

    public static void main(String[] args) {
        long sum = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < 5_000_000; i++) {
            Packet pkt = new Packet(i % 256, "pkt-" + i); // label never used
            sum += pkt.getPayload();
        }
        long elapsed = System.currentTimeMillis() - start;
        // sum of (i % 256) for i in [0, 5M)
        long expected = 0;
        for (int i = 0; i < 5_000_000; i++) expected += (i % 256);
        System.out.println("Correct: " + (sum == expected)); // Expected: Correct: true
        System.out.println("Time(ms): " + elapsed);
    }
}
