package com.wildfire.tracker;

import com.wildfire.tracker.service.WildfireRiskInferenceServiceTest;

/**
 * Console test suite runner for zero-dependency local testing.
 */
public class TestRunner {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("WILDFIRE RISK DETECTOR - BACKEND TEST SUITE");
        System.out.println("==================================================");

        try {
            new WildfireRiskInferenceServiceTest().runAllTests();

            System.out.println("\n--------------------------------------------------");
            System.out.println("ALL TESTS PASSED SUCCESSFULLY! (0 failures)");
            System.out.println("--------------------------------------------------");
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("\nTEST FAILED: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }
}
