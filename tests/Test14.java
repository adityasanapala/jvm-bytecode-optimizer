// Scenario: All Five Optimizations — MiniORM-style workload.
// Row.serialize() → mono (single concrete Column type) + inline (small body)
// table.rowCount read repeatedly → RLE
// freshly allocated Row is non-null → null-check elimination
// Row.debugId field never read → dead field elimination
// Expected output: Correct: true

public class Test14 {

    // ── Column hierarchy ──────────────────────────────────────────────────
    abstract static class Column {
        abstract int encode(int val);
    }

    static class IntColumn extends Column {
        @Override
        public int encode(int val) { return val; } // small → inlineable after mono
    }

    // ── Row ───────────────────────────────────────────────────────────────
    static class Row {
        int[]  data;
        String debugId; // never read anywhere → dead field

        Row(int cols, String id) {
            this.data    = new int[cols];
            this.debugId = id; // dead store
        }

        void set(int col, int val) { data[col] = val; }
        int  get(int col)          { return data[col]; }
    }

    // ── Table ─────────────────────────────────────────────────────────────
    static class Table {
        int    rowCount;  // read many times in loop → RLE target
        int    colCount;
        Column col;       // single concrete type → mono target

        Table(int rows, int cols) {
            this.rowCount = rows;
            this.colCount = cols;
            this.col      = new IntColumn();
        }

        long process() {
            long sum = 0;
            for (int r = 0; r < rowCount; r++) {        // rowCount: RLE
                Row row = new Row(colCount, "r" + r);   // new → non-null; debugId → dead
                for (int c = 0; c < colCount; c++) {    // colCount: RLE
                    row.set(c, r * colCount + c);
                    sum += col.encode(row.get(c));       // col.encode → mono+inline
                }
            }
            return sum;
        }
    }

    public static void main(String[] args) {
        Table t = new Table(100, 10);
        long result = 0;
        long start  = System.currentTimeMillis();
        for (int i = 0; i < 1_000; i++) {
            result += t.process();
        }
        long elapsed = System.currentTimeMillis() - start;

        // Expected: sum of (r*10+c) for r in [0,100), c in [0,10), times 1000
        long expected = 0;
        for (int r = 0; r < 100; r++)
            for (int c = 0; c < 10; c++)
                expected += r * 10 + c;
        expected *= 1_000;

        System.out.println("Correct: " + (result == expected)); // Expected: Correct: true
        System.out.println("Time(ms): " + elapsed);
    }
}
