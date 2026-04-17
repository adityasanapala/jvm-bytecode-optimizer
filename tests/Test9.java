// Scenario: Monomorphization — NEGATIVE case. Two concrete subclasses exist,
// so the virtual call has two targets and must NOT be devirtualized.
// Optimizer should leave the call site untouched. Correctness is the only metric here.
// Expected output: Correct: true

public class Test9 {

    abstract static class Vehicle {
        abstract String fuelType();
    }

    static class Car extends Vehicle {
        @Override
        String fuelType() { return "Petrol"; }
    }

    static class ElectricCar extends Vehicle {
        @Override
        String fuelType() { return "Electric"; }
    }

    public static void main(String[] args) {
        Vehicle[] fleet = { new Car(), new ElectricCar(), new Car(), new ElectricCar() };
        int petrol = 0, electric = 0;
        for (int i = 0; i < 1_000_000; i++) {
            // This call has 2 concrete targets — mono should NOT fire
            String f = fleet[i % fleet.length].fuelType();
            if (f.equals("Petrol"))   petrol++;
            if (f.equals("Electric")) electric++;
        }
        // Expected: 500000 each
        System.out.println("Correct: " + (petrol == 500_000 && electric == 500_000)); // Expected: Correct: true
    }
}
