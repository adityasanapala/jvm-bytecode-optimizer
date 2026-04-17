// Scenario: Null-Check Elimination — receiver allocated with 'new' is provably non-null.
// Explicit null guards around freshly allocated objects are eliminated.
// Expected output: Correct: true

public class Test11 {

    static class Node {
        int value;
        Node next;
        Node(int v) { this.value = v; this.next = null; }

        int getValue() { return value; }
    }

    static int sumList(int size) {
        Node head = new Node(0); // provably non-null after new
        Node cur  = head;
        for (int i = 1; i < size; i++) {
            cur.next = new Node(i); // new → non-null; null check on cur.next is redundant
            cur = cur.next;
        }
        // Traverse: each node was created with new, so never null mid-list
        int sum = 0;
        cur = head;
        while (cur != null) {
            sum += cur.getValue(); // null check on cur for virtual call is redundant after loop guard
            cur = cur.next;
        }
        return sum;
    }

    public static void main(String[] args) {
        long total = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1_000; i++) {
            total += sumList(1_000);
        }
        long elapsed = System.currentTimeMillis() - start;
        long expected = 1_000L * (1_000L * 999L / 2); // sum 0..999 * 1000 iterations
        System.out.println("Correct: " + (total == expected)); // Expected: Correct: true
        System.out.println("Time(ms): " + elapsed);
    }
}
