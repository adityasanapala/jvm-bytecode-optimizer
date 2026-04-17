// Scenario: Monomorphization — abstract base class, only one concrete subclass.
// Shape.area() resolves only to Circle.area() — virtual call devirtualized.
// Expected output: Correct: true

public class Test6 {

    abstract static class Shape {
        abstract double area();
    }

    static class Circle extends Shape {
        final double radius;
        Circle(double r) { this.radius = r; }

        @Override
        double area() { return 3.14159 * radius * radius; }
    }

    public static void main(String[] args) {
        Shape s = new Circle(5.0); // only Circle is ever constructed
        double total = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10_000_000; i++) {
            total += s.area(); // virtual call on abstract class → one concrete target
        }
        long elapsed = System.currentTimeMillis() - start;
        double expected = 3.14159 * 25.0 * 10_000_000;
        System.out.println("Correct: " + (Math.abs(total - expected) < 1.0)); // Expected: Correct: true
        System.out.println("Time(ms): " + elapsed);
    }
}
