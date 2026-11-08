package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the flight controller against a fake port: availability and permission detection,
 * adoption of an existing flight, stall detection, fluid pausing, and safe release that never
 * switches off a flight the user started.
 */
class FlightControllerTest {

    /** A scriptable {@link FlightPort}. */
    private static final class FakeFlight implements FlightPort {
        boolean available = true;
        boolean permitted = true;
        boolean flying;
        boolean inFluid;
        boolean onGround;
        double speed = 20;
        double horizontalSpeed = 20;
        int setFlyingCalls;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean flying() {
            return flying;
        }

        @Override
        public boolean permitted() {
            return permitted;
        }

        @Override
        public boolean setFlying(boolean value) {
            setFlyingCalls++;
            this.flying = value;
            return true;
        }

        @Override
        public void setSpeed(double blocksPerSecond) {
            this.speed = blocksPerSecond;
        }

        @Override
        public boolean inFluid() {
            return inFluid;
        }

        @Override
        public boolean onGround() {
            return onGround;
        }

        @Override
        public double horizontalSpeed() {
            return horizontalSpeed;
        }

        @Override
        public String source() {
            return available ? "creative" : "none";
        }
    }

    @Test
    void engagesWhenAvailableAndPermitted() {
        FakeFlight port = new FakeFlight();
        FlightController controller = new FlightController();
        FlightController.FlightResult result = controller.engage(port, 25);
        assertTrue(result.ok());
        assertTrue(port.flying());
        assertTrue(controller.engagedByUs());
        assertEquals(FlightController.FlightPhase.ENGAGED, controller.phase());
        assertEquals(25, port.speed);
    }

    @Test
    void reportsUnavailableRatherThanForcing() {
        FakeFlight port = new FakeFlight();
        port.available = false;
        FlightController controller = new FlightController();
        FlightController.FlightResult result = controller.engage(port, 25);
        assertFalse(result.ok());
        assertEquals(FlightController.FlightPhase.UNAVAILABLE, controller.phase());
        assertFalse(port.flying());
        assertEquals(0, port.setFlyingCalls);
    }

    @Test
    void refusesWhenNotPermitted() {
        FakeFlight port = new FakeFlight();
        port.permitted = false;
        FlightController controller = new FlightController();
        assertFalse(controller.engage(port, 25).ok());
        assertEquals(0, port.setFlyingCalls);
    }

    @Test
    void adoptsAnExistingFlightWithoutOverridingSpeed() {
        FakeFlight port = new FakeFlight();
        port.flying = true;
        port.speed = 7;
        FlightController controller = new FlightController();
        FlightController.FlightResult result = controller.engage(port, 25);
        assertTrue(result.ok());
        assertEquals(FlightController.FlightPhase.ADOPTED, controller.phase());
        assertFalse(controller.engagedByUs());
        assertEquals(7, port.speed, "must not overwrite the user's flight speed");
    }

    @Test
    void releaseLeavesAnAdoptedFlightAlone() {
        FakeFlight port = new FakeFlight();
        port.flying = true;
        FlightController controller = new FlightController();
        controller.engage(port, 25);
        controller.release(port);
        assertTrue(port.flying(), "a flight the user started must be left on");
    }

    @Test
    void releaseSwitchesOffAFlightWeStarted() {
        FakeFlight port = new FakeFlight();
        FlightController controller = new FlightController();
        controller.engage(port, 25);
        controller.release(port);
        assertFalse(port.flying());
        assertEquals(FlightController.FlightPhase.INACTIVE, controller.phase());
    }

    @Test
    void detectsAStallAndReleases() {
        FakeFlight port = new FakeFlight();
        FlightController controller = new FlightController();
        controller.engage(port, 25);
        port.horizontalSpeed = 0;
        port.onGround = false;
        boolean healthy = true;
        for (int i = 0; i < FlightController.STALL_TICKS; i++) {
            healthy = controller.maintain(port, 1000, 1000, 0, 0);
        }
        assertFalse(healthy);
        assertEquals(FlightController.FlightPhase.STALLED, controller.phase());
        assertFalse(port.flying(), "a stalled flight must be released");
    }

    @Test
    void resetsStallCounterWhenMoving() {
        FakeFlight port = new FakeFlight();
        FlightController controller = new FlightController();
        controller.engage(port, 25);
        port.horizontalSpeed = 0;
        for (int i = 0; i < FlightController.STALL_TICKS - 1; i++) {
            controller.maintain(port, 1000, 1000, 0, 0);
        }
        port.horizontalSpeed = 20;
        assertTrue(controller.maintain(port, 1000, 1000, 0, 0));
        assertEquals(0, controller.stallTicks());
    }

    @Test
    void fluidPausesRatherThanStalls() {
        FakeFlight port = new FakeFlight();
        FlightController controller = new FlightController();
        controller.engage(port, 25);
        port.inFluid = true;
        assertFalse(controller.maintain(port, 1000, 1000, 0, 0));
        assertEquals(FlightController.FlightPhase.BLOCKED, controller.phase());
        assertEquals(0, controller.stallTicks());
    }

    @Test
    void maintainIsSafeWhenInactive() {
        FakeFlight port = new FakeFlight();
        FlightController controller = new FlightController();
        assertFalse(controller.maintain(port, 0, 0, 0, 0));
    }
}
