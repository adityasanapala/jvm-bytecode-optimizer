// Scenario: Monomorphization — single concrete subclass, virtual call has exactly one target.
// The call to animal.speak() resolves only to Dog.speak(); optimizer should devirtualize it.
// Expected output (10000 iterations): "Woof" printed 10000 times.

class Animal {
    public String speak() { return "..."; }
}

class Dog extends Animal {
    @Override
    public String speak() { return "Woof"; }
}

public class Test1 {
    public static void main(String[] args) {
        Animal animal = new Dog(); // only Dog is ever instantiated
        long start = System.currentTimeMillis();
        int count = 0;
        for (int i = 0; i < 100_000; i++) {
            String s = animal.speak(); // virtual call — mono target: Dog.speak
            if (s.equals("Woof")) count++;
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Correct: " + (count == 100_000)); // Expected: Correct: true
        System.out.println("Time(ms): " + elapsed);
    }
}
