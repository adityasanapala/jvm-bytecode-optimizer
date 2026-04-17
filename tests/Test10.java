// Scenario: Combined optimization — all three passes fire together.
// BankAccount.deposit() is monomorphized (single concrete type), inlined (small body),
// and balance field reads inside getBalance() are RLE'd.
// Demonstrates realistic OO workload.
// Expected output: Correct: true

public class Test10 {

    abstract static class Account {
        abstract void deposit(int amount);
        abstract long getBalance();
    }

    static class BankAccount extends Account {
        private long balance = 0;
        private long txCount = 0;

        @Override
        public void deposit(int amount) {  // small body — mono + inline candidate
            balance += amount;
            txCount++;
        }

        @Override
        public long getBalance() {
            // balance read twice with no intervening store — RLE candidate
            long b1 = balance;
            long b2 = balance; // redundant load
            return (b1 + b2) / 2; // == balance
        }

        public long getTxCount() { return txCount; }
    }

    public static void main(String[] args) {
        BankAccount acct = new BankAccount();
        Account a = acct; // single concrete type — mono fires on a.deposit()

        long start = System.currentTimeMillis();
        for (int i = 0; i < 10_000_000; i++) {
            a.deposit(1); // virtual → devirtualized → inlined
        }
        long elapsed = System.currentTimeMillis() - start;

        long bal = acct.getBalance(); // RLE fires inside getBalance()
        long tx  = acct.getTxCount();

        System.out.println("Correct: " + (bal == 10_000_000L && tx == 10_000_000L)); // Expected: Correct: true
        System.out.println("Time(ms): " + elapsed);
    }
}
