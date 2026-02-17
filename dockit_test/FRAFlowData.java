package com.example.dockit_test;

import java.util.HashMap;
import java.util.Map;

public class FRAFlowData {
    String phoneNumber;
    String email;
    String phoneOtpTransactionId;
    String emailOtpTransactionId;
    String nidTransactionId;
    String biometricsTransactionId;
    Map<String, String> nidData = new HashMap<>();
    Map<String, String> transliteratedData = new HashMap<>();
    boolean phoneVerified = false;
    boolean emailVerified = false;
    boolean nidScanned = false;
    boolean biometricsVerified = false;
    boolean ntraVerified = false;
    boolean csoVerified = false;

    void reset() {
        phoneNumber = null;
        email = null;
        phoneOtpTransactionId = null;
        emailOtpTransactionId = null;
        nidTransactionId = null;
        biometricsTransactionId = null;
        nidData.clear();
        transliteratedData.clear();
        phoneVerified = false;
        emailVerified = false;
        nidScanned = false;
        biometricsVerified = false;
        ntraVerified = false;
        csoVerified = false;
    }
}
