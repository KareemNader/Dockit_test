package com.example.dockit_test;

import static androidx.camera.core.CameraXThreads.TAG;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import com.vidv.dockit.generated.VIDVDocKitDocType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import me.vidv.vidvauthsdk.global.VIDVAuthFlow;
import me.vidv.vidvdockitsdk.global.VIDVCaptureMode;
import me.vidv.vidvlivenesssdk.sdk.VIDVLivenessConfig;
import me.vidv.vidvlivenesssdk.sdk.VIDVLivenessListener;
import me.vidv.vidvlivenesssdk.sdk.VIDVLivenessResponse;
import me.vidv.vidvlivenesssdk.sdk.CapturedActions;
import me.vidv.vidvlivenesssdk.sdk.BuilderError;
import me.vidv.vidvlivenesssdk.sdk.ServiceFailure;
import me.vidv.vidvlivenesssdk.sdk.UserExit;
import me.vidv.vidvlivenesssdk.sdk.Success;
import me.vidv.vidvdockitsdk.global.VIDVDocKitConfig;
import me.vidv.vidvdockitsdk.global.VIDVDocKitListener;
import me.vidv.vidvdockitsdk.global.VIDVDocKitResponse;
import me.vidv.vidvdockitsdk.global.VIDVDocKitData;
import me.vidv.vidvocrsdk.sdk.VIDVOCRConfig;
import me.vidv.vidvocrsdk.sdk.VIDVOCRListener;
import me.vidv.vidvocrsdk.sdk.VIDVOCRResponse;
import me.vidv.vidvocrsdk.viewmodel.VIDVOCRResult;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import me.vidv.vidvauthsdk.global.VIDVAuthConfig;
import me.vidv.vidvauthsdk.global.VIDVAuthListener;
import me.vidv.vidvauthsdk.global.VIDVAuthResponse;
import me.vidv.vidvauthsdk.global.VIDVAuthService;
import me.vidv.vidvauthsdk.global.ValifyConfig;


public class MainActivity extends AppCompatActivity {
    private void debugLog(String message) {
        Log.e("FRA_Debug", message);

        // Also write to file
        writeToDebugFile(message);

        // Show in UI
        runOnUiThread(() -> {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            resultText.append("\n[" + timestamp + "] " + message);
        });
    }

    private void writeToDebugFile(String message) {
        try {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(new java.util.Date());
            String logMessage = timestamp + " - " + message + "\n";

            // Write to app's internal storage (no permissions needed)
            File logFile = new File(getFilesDir(), "fra_debug.txt");

            java.io.FileWriter writer = new java.io.FileWriter(logFile, true); // append mode
            writer.write(logMessage);
            writer.flush();
            writer.close();

        } catch (Exception e) {
            Log.e("FRA_Debug", "Failed to write to file: " + e.getMessage());
        }
    }

    private void clearDebugFile() {
        try {
            File logFile = new File(getFilesDir(), "fra_debug.txt");
            if (logFile.exists()) {
                logFile.delete();
            }
            // Create fresh file
            logFile.createNewFile();
            writeToDebugFile("=== DEBUG LOG STARTED ===");
        } catch (Exception e) {
            Log.e("FRA_Debug", "Failed to clear file: " + e.getMessage());
        }
    }

    private void showDebugFile() {
        try {
            File logFile = new File(getFilesDir(), "fra_debug.txt");
            if (!logFile.exists()) {
                Toast.makeText(this, "No debug file found", Toast.LENGTH_SHORT).show();
                return;
            }

            // Read the file
            StringBuilder content = new StringBuilder();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(logFile));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            // Show in dialog
            new AlertDialog.Builder(this)
                    .setTitle("Debug Log")
                    .setMessage(content.toString())
                    .setPositiveButton("OK", null)
                    .setNegativeButton("Clear", (dialog, which) -> clearDebugFile())
                    .show();

        } catch (Exception e) {
            Toast.makeText(this, "Error reading file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    TextView resultText;
    private TextView tvResult;
    private String frontTransactionId; // Store the transaction ID

    private Button btnStartFRAFlow;
    private FRAFlowData fraFlowData; // Store flow data
    private enum FRAFlowStep {
        IDLE, PHONE_OTP, EMAIL_OTP, NID_SCAN, BIOMETRICS, NTRA_CHECK, CSO_CHECK, DIGITAL_IDENTITY, COMPLETE
    }
    private FRAFlowStep currentStep = FRAFlowStep.IDLE;


    private Button btnQueryTransaction;
    private ValifyDecryption valifyDecryption;


    private String BASE_URL = "https://www.valifystage.com";
    private String SANCTION_API_URL = "https://www.valifystage.com/api/v2/sanction-shield/search/";
    private String TRANSLITERATION_API_URL = "https://valifystage.com/api/v1/transliterate/egy-nid/";

    private String USERNAME = "personal_usage__49016_integration_bundle";
    private String PASSWORD = "E8szpURo8KowzDRR";
    private String CLIENT_ID = "WBoBMwKEBFFxrzE8VGqTufLB3nKgTRhDn8tQyhmn";
    private String CLIENT_SECRET = "CfQGG9TryeSURlImPlJbjNvWmTbFVrRudzNcaCyWx1cyOGzmys5TUjmPs2BM0HFwpUEVmF4Aa4xpqVUBkInf09xbrK2BusA64KeFYHn5EtWVlizZErdvnc69ETloENDM";

    private Button startDockitButton;
    private Button transliterateButton;
    private AccessTokenGenerator tokenGenerator;
    private String accessToken = null;

    // Store the last DocKit scan data
    private Map<String, String> lastScannedData = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        resultText = findViewById(R.id.resultText);

        btnQueryTransaction = findViewById(R.id.btnQueryTransaction);
        // Initialize ValifyDecryption with your private key
        try {
            String privateKey = "-----BEGIN PRIVATE KEY-----\n" +
                    "MIICXAIBAAKBgQCDpcDtr6sxsE8rJavCeejvkWfdjTRyLZ5xCpp6uSYVk3sH50xI\n" +
                    "p1TQNtUheiFTI+JMbJ4Mz9aR+bwb8nsbxHrug2HVbmeE4lq3NNss7F4zA0Y5bLbm\n" +
                    "Ag5RENq4EDG56fM7DjblLzp7Rp/9SlpWMdXvmAtVkT13TR03WKuBJjiiQQIDAQAB\n" +
                    "AoGAdkVtxJwz0xowpfTcEIXxzXj2tUZsvb9aPvhlvKemHXA38evMzuD2A3GfnMna\n" +
                    "MpVkc4CJpbz7an3Qj9MS4ulr/po+eau8L1fKhOOMwYopw5xfZ6CCcQdfEbk6H8h6\n" +
                    "fJIslZQOF+Y0lDnBnreEzyxpQeUXd9pZV9UcCBKaZZNlZVkCQQDWzStcOEh70oz/\n" +
                    "fSjkeHYIe2qDprww+4aTejPrHSwPSuzbHANpZyUrJjBOlr+188xfOb0uyOTKtJ4x\n" +
                    "OtXpuOubAkEAnOWxjHM+BJ/0O3/bCvhZOBpZnq1+EFg9oap2TpW6VL5XkjJVyLpX\n" +
                    "oug6fsm07ovWcaQM63aoliGvtWZAGC4tUwJBAL9yD6Ja+6d4qniP6eFvx+uZa/64\n" +
                    "neSeWXyaHyn/TyS2J9LF7fiEmOkTWVzCGU4nY9C/mnDXVqugPZotETkFut8CQBy7\n" +
                    "Dw06GTQ9mjq/CfxzR9s3MAwXlwslLXwKPAnd7zYPePfDkePlA6FIR1XqV+CK6OT8\n" +
                    "doUzwGFln8hnBfunkRMCQFLZP0APkfTlCuaytIR6F1WvDKySG0J4sDPCKT+k0ay4\n" +
                    "c/zfbrwHuACID1fVr+E5W+D22z5Qo3uNwZbJRFgqBiA=\n" +
                    "-----END PRIVATE KEY-----";
            valifyDecryption = new ValifyDecryption(this, privateKey);
        } catch (Exception e) {
            Log.e("Valify", "Error initializing decryption: " + e.getMessage());
        }

        Button btnStart = findViewById(R.id.btnStartLiveness);
        Button btnCheck = findViewById(R.id.checkSanctionButton);
        startDockitButton = findViewById(R.id.startDockitButton);
        transliterateButton = findViewById(R.id.transliterateButton);
        tvResult = findViewById(R.id.resultText);
        tokenGenerator = new AccessTokenGenerator();

        btnQueryTransaction.setOnClickListener(v -> {
            resultText.setText("Querying transaction...");

            // Generate token first
            tokenGenerator.generateAccessToken(
                    USERNAME, PASSWORD, CLIENT_ID, CLIENT_SECRET, BASE_URL, this,
                    new AccessTokenGenerator.AccessTokenCallback() {
                        @Override
                        public void onSuccess(AccessTokenResponse tokenResponse) {
                            // Query a specific transaction
                            valifyDecryption.queryTransaction(
                                    BASE_URL,
                                    tokenResponse.getAccessToken(),
                                    "5f0fd53872324f52afafb2fb668df379",
                                    "764434",
                                    new ValifyDecryption.DecryptionCallback() {
                                        @Override
                                        public void onSuccess(JSONObject decryptedData) {
                                            runOnUiThread(() -> {
                                                try {
                                                    resultText.setText("Decrypted Data:\n\n" +
                                                            decryptedData.toString(2));
                                                } catch (JSONException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            });
                                        }

                                        @Override
                                        public void onFailure(String error) {
                                            runOnUiThread(() -> {
                                                resultText.setText("Error: " + error);
                                            });
                                        }
                                    }
                            );
                        }

                        @Override
                        public void onFailure(Exception e) {
                            resultText.setText("Token generation failed: " + e.getMessage());
                        }
                    }
            );
        });

        btnStart.setOnClickListener(view -> {
            if (accessToken == null) {
                generateTokenAndStart();
            } else {
                startLivenessSDK();
            }
        });

        btnCheck.setOnClickListener(v -> {
            Log.d("TEST", "Button pressed!");
            tvResult.setText("Checking...");
            AccessTokenGenerator generator = new AccessTokenGenerator();
            generator.generateAccessToken(USERNAME, PASSWORD, CLIENT_ID, CLIENT_SECRET, BASE_URL, this,
                    new AccessTokenGenerator.AccessTokenCallback() {
                        @Override
                        public void onSuccess(AccessTokenResponse tokenResponse) {
                            callSanctionApi(tokenResponse.getAccessToken());
                        }

                        @Override
                        public void onFailure(Exception e) {
                            tvResult.setText("Token generation failed: " + e.getMessage());
                        }
                    });
        });

        startDockitButton.setOnClickListener(v -> generateTokenAndStartSDK());

        // Transliterate button click
        transliterateButton.setOnClickListener(v -> {
            if (lastScannedData.isEmpty()) {
                Toast.makeText(this, "No scanned data available. Please scan an ID first.", Toast.LENGTH_SHORT).show();
            } else {
                generateTokenAndTransliterate();
            }
        });

        btnStartFRAFlow = findViewById(R.id.btnStartFRAFlow);
        fraFlowData = new FRAFlowData();

        //btnStartFRAFlow.setOnClickListener(v -> createDigitalIdentity());

        Button btnViewDebugLog = findViewById(R.id.btnViewDebugLog);
        btnViewDebugLog.setOnClickListener(v -> showDebugFile());

// Clear log at start of FRA flow
        btnStartFRAFlow.setOnClickListener(v -> {
            clearDebugFile(); // Clear old logs
            createDigitalIdentity();
        });

    }

    private void startFRAFlow() {
        // Reset flow data
        fraFlowData.reset();
        currentStep = FRAFlowStep.IDLE;

        new AlertDialog.Builder(this)
                .setTitle("Start FRA KYC Flow")
                .setMessage("This will guide you through the complete KYC verification process:\n\n" +
                        "1. Phone OTP Verification\n" +
                        "2. Email OTP Verification\n" +
                        "3. National ID Scan (OCR)\n" +
                        "4. Biometric Verification\n" +
                        "5. NTRA Validation\n" +
                        "6. CSO Submission\n" +
                        "7. Digital Identity Creation\n\n" +
                        "Are you ready to begin?")
                .setPositiveButton("Start", (dialog, which) -> {
                    resultText.setText("Starting FRA Flow...\n");
                    collectUserContactInfo();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void collectUserContactInfo() {
        // Create dialog to collect phone and email
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(android.R.layout.simple_list_item_2, null);

        // You should create a custom layout with EditTexts for phone and email
        // For now, using a simplified approach with AlertDialog

        final android.widget.EditText phoneInput = new android.widget.EditText(this);
        phoneInput.setHint("Enter phone number (e.g., +201234567890)");
        phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);

        final android.widget.EditText emailInput = new android.widget.EditText(this);
        emailInput.setHint("Enter email address");
        emailInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(phoneInput);
        layout.addView(emailInput);

        new AlertDialog.Builder(this)
                .setTitle("Contact Information")
                .setMessage("Enter your phone number and email for OTP verification:")
                .setView(layout)
                .setPositiveButton("Continue", (dialog, which) -> {
                    fraFlowData.phoneNumber = phoneInput.getText().toString().trim();
                    fraFlowData.email = emailInput.getText().toString().trim();

                    if (fraFlowData.phoneNumber.isEmpty() || fraFlowData.email.isEmpty()) {
                        Toast.makeText(this, "Both phone and email are required", Toast.LENGTH_SHORT).show();
                        collectUserContactInfo(); // Retry
                    } else {
                        startPhoneOTPVerification();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "FRA Flow cancelled", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    private void startPhoneOTPVerification() {
        currentStep = FRAFlowStep.PHONE_OTP;
        resultText.setText("Step 1/7: Phone OTP Verification\n\nSending OTP to: " + fraFlowData.phoneNumber);

        // Generate token and send OTP
        tokenGenerator.generateAccessToken(
                USERNAME, PASSWORD, CLIENT_ID, CLIENT_SECRET, BASE_URL, this,
                new AccessTokenGenerator.AccessTokenCallback() {
                    @Override
                    public void onSuccess(AccessTokenResponse tokenResponse) {
                        sendPhoneOTP(tokenResponse.getAccessToken());
                    }

                    @Override
                    public void onFailure(Exception e) {
                        handleFRAFlowError("Token generation failed: " + e.getMessage());
                    }
                }
        );
    }

    private void sendPhoneOTP(String accessToken) {
        // Call Phone OTP API
        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("bundle_key", "5f0fd53872324f52afafb2fb668df379");
            requestBody.put("phone_number", fraFlowData.phoneNumber);

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/v1/otp/send/")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handleFRAFlowError("Phone OTP send failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();

                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            fraFlowData.phoneOtpTransactionId = jsonResponse.getString("transaction_id");

                            resultText.append("\n✓ OTP sent successfully!");
                            promptForPhoneOTP();

                        } catch (JSONException e) {
                            handleFRAFlowError("Error parsing OTP response: " + e.getMessage());
                        }
                    });
                }
            });

        } catch (Exception e) {
            handleFRAFlowError("Error sending phone OTP: " + e.getMessage());
        }
    }


    private void promptForPhoneOTP() {
        final android.widget.EditText otpInput = new android.widget.EditText(this);
        otpInput.setHint("Enter 6-digit OTP");
        otpInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(this)
                .setTitle("Phone Verification")
                .setMessage("Enter the phone OTP for: " + fraFlowData.phoneNumber)
                .setView(otpInput)
                .setPositiveButton("Verify", (dialog, which) -> {
                    String otp = otpInput.getText().toString().trim();
                    if (otp.length() == 6) {
                        verifyPhoneOTP(otp);
                    } else {
                        Toast.makeText(this, "OTP must be 6 digits", Toast.LENGTH_SHORT).show();
                        promptForPhoneOTP();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "FRA Flow cancelled", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    private void verifyPhoneOTP(String otp) {
        resultText.append("\n\nVerifying phone OTP...");

        tokenGenerator.generateAccessToken(
                USERNAME, PASSWORD, CLIENT_ID, CLIENT_SECRET, BASE_URL, this,
                new AccessTokenGenerator.AccessTokenCallback() {
                    @Override
                    public void onSuccess(AccessTokenResponse tokenResponse) {
                        callPhoneOTPVerifyAPI(tokenResponse.getAccessToken(), otp);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        handleFRAFlowError("Token generation failed: " + e.getMessage());
                    }
                }
        );
    }

    private void callPhoneOTPVerifyAPI(String accessToken, String otp) {
        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("bundle_key", "5f0fd53872324f52afafb2fb668df379");
            requestBody.put("transaction_id", fraFlowData.phoneOtpTransactionId);
            requestBody.put("otp", otp);

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/v1/otp/verify/")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handleFRAFlowError("Phone OTP verification failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();

                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            boolean verified = jsonResponse.getBoolean("verified");

                            if (verified) {
                                fraFlowData.phoneVerified = true;
                                resultText.append("\n✓ Phone verified successfully!\n");

                                // Move to email OTP
                                new android.os.Handler().postDelayed(() -> {
                                    startEmailOTPVerification();
                                }, 1500);
                            } else {
                                Toast.makeText(MainActivity.this, "Invalid OTP. Please try again.", Toast.LENGTH_SHORT).show();
                                promptForPhoneOTP();
                            }

                        } catch (JSONException e) {
                            handleFRAFlowError("Error parsing verification response: " + e.getMessage());
                        }
                    });
                }
            });

        } catch (Exception e) {
            handleFRAFlowError("Error verifying phone OTP: " + e.getMessage());
        }
    }

    private void startEmailOTPVerification() {
        currentStep = FRAFlowStep.EMAIL_OTP;
        resultText.append("\nStep 2/7: Email OTP Verification\n\nSending OTP to: " + fraFlowData.email);

        tokenGenerator.generateAccessToken(
                USERNAME, PASSWORD, CLIENT_ID, CLIENT_SECRET, BASE_URL, this,
                new AccessTokenGenerator.AccessTokenCallback() {
                    @Override
                    public void onSuccess(AccessTokenResponse tokenResponse) {
                        sendEmailOTP(tokenResponse.getAccessToken());
                    }

                    @Override
                    public void onFailure(Exception e) {
                        handleFRAFlowError("Token generation failed: " + e.getMessage());
                    }
                }
        );
    }

    private void sendEmailOTP(String accessToken) {
        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("bundle_key", "5f0fd53872324f52afafb2fb668df379");
            requestBody.put("email", fraFlowData.email);

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/v1/otp/email/send/")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handleFRAFlowError("Email OTP send failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();

                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            fraFlowData.emailOtpTransactionId = jsonResponse.getString("transaction_id");

                            resultText.append("\n✓ OTP sent successfully!");
                            promptForEmailOTP();

                        } catch (JSONException e) {
                            handleFRAFlowError("Error parsing OTP response: " + e.getMessage());
                        }
                    });
                }
            });

        } catch (Exception e) {
            handleFRAFlowError("Error sending email OTP: " + e.getMessage());
        }
    }

    private void promptForEmailOTP() {
        final android.widget.EditText otpInput = new android.widget.EditText(this);
        otpInput.setHint("Enter 6-digit OTP");
        otpInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(this)
                .setTitle("Email Verification")
                .setMessage("Enter the OTP sent to " + fraFlowData.email)
                .setView(otpInput)
                .setPositiveButton("Verify", (dialog, which) -> {
                    String otp = otpInput.getText().toString().trim();
                    if (otp.length() == 6) {
                        verifyEmailOTP(otp);
                    } else {
                        Toast.makeText(this, "OTP must be 6 digits", Toast.LENGTH_SHORT).show();
                        promptForEmailOTP();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "FRA Flow cancelled", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    private void verifyEmailOTP(String otp) {
        resultText.append("\n\nVerifying email OTP...");

        tokenGenerator.generateAccessToken(
                USERNAME, PASSWORD, CLIENT_ID, CLIENT_SECRET, BASE_URL, this,
                new AccessTokenGenerator.AccessTokenCallback() {
                    @Override
                    public void onSuccess(AccessTokenResponse tokenResponse) {
                        callEmailOTPVerifyAPI(tokenResponse.getAccessToken(), otp);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        handleFRAFlowError("Token generation failed: " + e.getMessage());
                    }
                }
        );
    }

    private void callEmailOTPVerifyAPI(String accessToken, String otp) {
        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("bundle_key", "5f0fd53872324f52afafb2fb668df379");
            requestBody.put("transaction_id", fraFlowData.emailOtpTransactionId);
            requestBody.put("otp", otp);

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/v1/otp/email/verify/")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handleFRAFlowError("Email OTP verification failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();

                    Log.e("EmailOTP", "=== ACTUAL RESPONSE ===");
                    Log.e("EmailOTP", "Status code: " + response.code());
                    Log.e("EmailOTP", "Response body: " + responseBody);
                    Log.e("EmailOTP", "=====================");

                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            boolean verified = jsonResponse.getBoolean("verified");

                            if (verified) {
                                fraFlowData.emailVerified = true;
                                resultText.append("\n✓ Email verified successfully!\n");

                                // Move to NID scan
                                new android.os.Handler().postDelayed(() -> {
                                    startNIDScan();
                                }, 1500);
                            } else {
                                Toast.makeText(MainActivity.this, "Invalid OTP. Please try again.", Toast.LENGTH_SHORT).show();
                                promptForEmailOTP();
                            }

                        } catch (JSONException e) {
                            handleFRAFlowError("Error parsing verification response: " + e.getMessage());
                        }
                    });
                }
            });

        } catch (Exception e) {
            handleFRAFlowError("Error verifying email OTP: " + e.getMessage());
        }
    }

    private void startNIDScan() {
        currentStep = FRAFlowStep.NID_SCAN;
        resultText.append("\nStep 3/7: National ID Scan\n\nPreparing camera...");

        new android.os.Handler().postDelayed(() -> {
            tokenGenerator.generateAccessToken(
                    USERNAME, PASSWORD, CLIENT_ID, CLIENT_SECRET, BASE_URL, this,
                    new AccessTokenGenerator.AccessTokenCallback() {
                        @Override
                        public void onSuccess(AccessTokenResponse tokenResponse) {
                            launchDocKitForFRAFlow(tokenResponse.getAccessToken());
                        }

                        @Override
                        public void onFailure(Exception e) {
                            handleFRAFlowError("Token generation failed: " + e.getMessage());
                        }
                    }
            );
        }, 1000);
    }

    private void launchDocKitForFRAFlow(String accessToken) {
        VIDVDocKitConfig.Builder docKitBuilder = new VIDVDocKitConfig.Builder()
                .setBaseUrl(BASE_URL + "/")
                .setAccessToken(accessToken)
                .setBundleKey("5f0fd53872324f52afafb2fb668df379")
                .setDocumentType(VIDVDocKitDocType.egyNID())
                .setLanguage("en")
                .previewCapturedImage(true)
                .setCaptureMode(VIDVCaptureMode.MANUAL.INSTANCE);

        docKitBuilder.start(this, new VIDVDocKitListener() {
            @Override
            public void onDocKitResult(VIDVDocKitResponse result) {
                if (result instanceof VIDVDocKitResponse.Success) {
                    VIDVDocKitResponse.Success success = (VIDVDocKitResponse.Success) result;
                    VIDVDocKitData data = success.getData();

                    String sessionId = data.getSessionID();
                    String tempTransactionId = null;

                    Map<String, Object> extractedData = data.getExtractedData();

                    if (extractedData != null) {
                        Log.e("FRA_DocKit", "=== ALL EXTRACTED DATA KEYS ===");
                        for (String key : extractedData.keySet()) {
                            Object value = extractedData.get(key);
                            Log.e("FRA_DocKit", key + " = " + value);
                        }

                        if (extractedData.containsKey("transaction_id")) {
                            tempTransactionId = extractedData.get("transaction_id").toString();
                        } else if (extractedData.containsKey("transactionId")) {
                            tempTransactionId = extractedData.get("transactionId").toString();
                        } else if (extractedData.containsKey("front_transaction_id")) {
                            tempTransactionId = extractedData.get("front_transaction_id").toString();
                        } else if (extractedData.containsKey("frontTransactionId")) {
                            tempTransactionId = extractedData.get("frontTransactionId").toString();
                        }

                        if (tempTransactionId == null && extractedData.containsKey("result")) {
                            Object resultObj = extractedData.get("result");
                            if (resultObj instanceof Map) {
                                Map<String, Object> resultMap = (Map<String, Object>) resultObj;
                                if (resultMap.containsKey("transaction_id")) {
                                    tempTransactionId = resultMap.get("transaction_id").toString();
                                } else if (resultMap.containsKey("transactionId")) {
                                    tempTransactionId = resultMap.get("transactionId").toString();
                                }
                            }
                        }
                    }

                    final String transactionId = tempTransactionId;

                    Log.e("FRA_DocKit", "Session ID: " + sessionId);
                    Log.e("FRA_DocKit", "Transaction ID found: " + transactionId);

                    fraFlowData.nidTransactionId = transactionId != null ? transactionId : sessionId;
                    fraFlowData.nidScanned = true;

                    extractAndStoreFRANIDData(success);

                    runOnUiThread(() -> {
                        resultText.append("\n✓ National ID scanned successfully!");
                        resultText.append("\n[DEBUG] Session ID: " + sessionId);
                        resultText.append("\n[DEBUG] Transaction ID: " + transactionId);
                        resultText.append("\n[DEBUG] Using for Liveness: " + fraFlowData.nidTransactionId);

                        Toast.makeText(MainActivity.this,
                                "Transaction ID: " + fraFlowData.nidTransactionId,
                                Toast.LENGTH_LONG).show();

                        new android.os.Handler().postDelayed(() -> {
                            startBiometricsVerification();
                        }, 1500);
                    });

                } else if (result instanceof VIDVDocKitResponse.ServiceFailure) {
                    VIDVDocKitResponse.ServiceFailure fail = (VIDVDocKitResponse.ServiceFailure) result;
                    handleFRAFlowError("NID scan failed: " + fail.getMessage());

                } else if (result instanceof VIDVDocKitResponse.Exit) {
                    handleFRAFlowError("User cancelled NID scan");
                }
            }
        });
    }

    private void extractAndStoreFRANIDData(VIDVDocKitResponse.Success success) {
        fraFlowData.nidData.clear();

        try {
            VIDVDocKitData data = success.getData();
            if (data != null) {
                Map<String, Object> extractedData = data.getExtractedData();

                if (extractedData != null && !extractedData.isEmpty()) {
                    if (extractedData.containsKey("result")) {
                        Object resultObj = extractedData.get("result");
                        if (resultObj instanceof Map) {
                            Map<String, Object> resultMap = (Map<String, Object>) resultObj;
                            for (Map.Entry<String, Object> entry : resultMap.entrySet()) {
                                if (entry.getValue() != null) {
                                    fraFlowData.nidData.put(entry.getKey(), entry.getValue().toString());
                                }
                            }
                        }
                    } else {
                        for (Map.Entry<String, Object> entry : extractedData.entrySet()) {
                            if (entry.getValue() != null) {
                                fraFlowData.nidData.put(entry.getKey(), entry.getValue().toString());
                            }
                        }
                    }

                    Log.d("FRAFlow", "NID data extracted: " + fraFlowData.nidData.size() + " fields");
                }
            }
        } catch (Exception e) {
            Log.e("FRAFlow", "Error extracting NID data: " + e.getMessage());
        }
    }

    private void startBiometricsVerification() {
        currentStep = FRAFlowStep.BIOMETRICS;
        resultText.append("\nStep 4/7: Biometric Verification\n\nPreparing face verification...");

        new android.os.Handler().postDelayed(() -> {
            tokenGenerator.generateAccessToken(
                    USERNAME, PASSWORD, CLIENT_ID, CLIENT_SECRET, BASE_URL, this,
                    new AccessTokenGenerator.AccessTokenCallback() {
                        @Override
                        public void onSuccess(AccessTokenResponse tokenResponse) {
                            launchLivenessForFRAFlow(tokenResponse.getAccessToken());
                        }

                        @Override
                        public void onFailure(Exception e) {
                            handleFRAFlowError("Token generation failed: " + e.getMessage());
                        }
                    }
            );
        }, 1000);
    }

    private void launchLivenessForFRAFlow(String accessToken) {
        Log.e("FRA_Liveness", "=== LIVENESS DEBUG ===");
        Log.e("FRA_Liveness", "NID Transaction ID: " + fraFlowData.nidTransactionId);
        Log.e("FRA_Liveness", "Access Token: " + (accessToken != null ? "Present" : "NULL"));
        Log.e("FRA_Liveness", "Base URL: " + BASE_URL);

        // SHOW DEBUG INFO IN APP
        runOnUiThread(() -> {
            resultText.append("\n[DEBUG] Using Transaction ID: " + fraFlowData.nidTransactionId);
            resultText.append("\n[DEBUG] Token: " + (accessToken != null ? "Present" : "NULL"));
            resultText.append("\n[DEBUG] Base URL: " + BASE_URL);
        });

        VIDVLivenessConfig.Builder builder = new VIDVLivenessConfig.Builder()
                .setBaseUrl(BASE_URL)
                .setAccessToken(accessToken)
                .setBundleKey("5f0fd53872324f52afafb2fb668df379")
                .setCollectUserInfo(true)
                .setNumberOfInstructions(3)
                .setLanguage("en")
                .setInstructionTimer(10)
                .showErrorDialogs(true)
                .setFrontTransactionId(fraFlowData.nidTransactionId);

        VIDVLivenessListener listener = new VIDVLivenessListener() {
            @Override
            public void onLivenessResult(VIDVLivenessResponse response) {
                runOnUiThread(() -> {
                    Log.e("FRA_Liveness", "Got liveness result: " + response.getClass().getSimpleName());

                    if (response instanceof Success) {
                        fraFlowData.biometricsVerified = true;
                        resultText.append("\n✓ Biometric verification successful!\n");

                        // Move to NTRA validation
                        new android.os.Handler().postDelayed(() -> {
                            createDigitalIdentity();
                        }, 1500);

                    } else if (response instanceof ServiceFailure) {
                        handleFRAFlowError("Biometrics failed: " + ((ServiceFailure) response).errorMessage);
                    } else if (response instanceof UserExit) {
                        handleFRAFlowError("User cancelled biometric verification");
                    }
                });
            }
        };

        builder.start(MainActivity.this, listener);
    }

    private void startNTRAValidation() {
        currentStep = FRAFlowStep.NTRA_CHECK;
        resultText.append("\nStep 5/7: NTRA Validation\n\nValidating with NTRA...");

        // Call NTRA API
        tokenGenerator.generateAccessToken(
                USERNAME, PASSWORD, CLIENT_ID, CLIENT_SECRET, BASE_URL, this,
                new AccessTokenGenerator.AccessTokenCallback() {
                    @Override
                    public void onSuccess(AccessTokenResponse tokenResponse) {
                        callNTRAAPI(tokenResponse.getAccessToken());
                    }

                    @Override
                    public void onFailure(Exception e) {
                        handleFRAFlowError("Token generation failed: " + e.getMessage());
                    }
                }
        );
    }

    private void callNTRAAPI(String accessToken) {
        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("bundle_key", "5f0fd53872324f52afafb2fb668df379");
            requestBody.put("bundle_session_id", fraFlowData.nidTransactionId);
            requestBody.put("nid", extractNIDFromData());
            requestBody.put("phone_number", fraFlowData.phoneNumber);

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/v1/fra/ntra/")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Valify-reference-userid", generateUniqueUserId())
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handleFRAFlowError("NTRA validation failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();

                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);

                            if (jsonResponse.has("result")) {
                                JSONObject result = jsonResponse.getJSONObject("result");
                                boolean isMatched = result.getBoolean("isMatched");

                                if (isMatched) {
                                    fraFlowData.ntraVerified = true;
                                    resultText.append("\n✓ NTRA validation successful!\n");

                                    // Move to CSO submission
                                    new android.os.Handler().postDelayed(() -> {
                                        startCSOSubmission();
                                    }, 1500);
                                } else {
                                    handleFRAFlowError("NTRA validation failed: Phone number does not match NID");
                                }
                            } else {
                                handleFRAFlowError("NTRA validation error: " + responseBody);
                            }

                        } catch (JSONException e) {
                            handleFRAFlowError("Error parsing NTRA response: " + e.getMessage());
                        }
                    });
                }
            });

        } catch (Exception e) {
            handleFRAFlowError("Error calling NTRA API: " + e.getMessage());
        }
    }

    private void startCSOSubmission() {
        currentStep = FRAFlowStep.CSO_CHECK;
        resultText.append("\nStep 6/7: CSO Submission\n\nSubmitting to CSO...");

        // Call CSO API
        tokenGenerator.generateAccessToken(
                USERNAME, PASSWORD, CLIENT_ID, CLIENT_SECRET, BASE_URL, this,
                new AccessTokenGenerator.AccessTokenCallback() {
                    @Override
                    public void onSuccess(AccessTokenResponse tokenResponse) {
                        callCSOAPI(tokenResponse.getAccessToken());
                    }

                    @Override
                    public void onFailure(Exception e) {
                        handleFRAFlowError("Token generation failed: " + e.getMessage());
                    }
                }
        );
    }

    private void callCSOAPI(String accessToken) {
        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("bundle_key", "5f0fd53872324f52afafb2fb668df379");
            requestBody.put("bundle_session_id", fraFlowData.nidTransactionId);
            requestBody.put("nid", extractNIDFromData());
            requestBody.put("first_name", extractFirstNameFromData());
            requestBody.put("full_name", extractFullNameFromData());
            requestBody.put("serial_number", extractSerialNumberFromData());
            requestBody.put("expiration", extractExpirationFromData());

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/v1.1/fra/cso/")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Valify-reference-userid", generateUniqueUserId())
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handleFRAFlowError("CSO submission failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();

                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);

                            if (jsonResponse.has("result")) {
                                JSONObject result = jsonResponse.getJSONObject("result");
                                boolean isValid = result.getBoolean("isValid");

                                if (isValid) {
                                    fraFlowData.csoVerified = true;
                                    resultText.append("\n✓ CSO submission successful!\n");

                                    // Move to digital identity creation
                                    new android.os.Handler().postDelayed(() -> {
                                        createDigitalIdentity();
                                    }, 1500);
                                } else {
                                    String errorMessage = result.optString("errorMessage", "Unknown error");
                                    handleFRAFlowError("CSO validation failed: " + errorMessage);
                                }
                            } else {
                                handleFRAFlowError("CSO submission error: " + responseBody);
                            }

                        } catch (JSONException e) {
                            handleFRAFlowError("Error parsing CSO response: " + e.getMessage());
                        }
                    });
                }
            });

        } catch (Exception e) {
            handleFRAFlowError("Error calling CSO API: " + e.getMessage());
        }
    }


    private void createDigitalIdentity() {
        currentStep = FRAFlowStep.DIGITAL_IDENTITY;
        resultText.append("\nStep 7/7: Creating Digital Identity\n");

        // Show dialog to choose between Registration or Login
        new AlertDialog.Builder(this)
                .setTitle("Digital Identity")
                .setMessage("Choose your action:")
                .setPositiveButton("Register New Account", (dialog, which) -> {
                    resultText.append("Starting registration...\n");
                    new android.os.Handler().postDelayed(() -> {
                        launchDigitalIdentitySDK(true); // true = registration
                    }, 1000);
                })
                .setNegativeButton("Login Existing Account", (dialog, which) -> {
                    resultText.append("Starting login...\n");
                    // For login, we need to ask for phone number
                    promptForLoginPhoneNumber();
                })
                .setCancelable(false)
                .show();
    }

    private void promptForLoginPhoneNumber() {
        final android.widget.EditText phoneInput = new android.widget.EditText(this);
        phoneInput.setHint("Enter your registered phone number");
        phoneInput.setText(fraFlowData.phoneNumber); // Pre-fill with the phone from OTP step
        phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);

        new AlertDialog.Builder(this)
                .setTitle("Login Phone Number")
                .setMessage("Enter the phone number you registered with:")
                .setView(phoneInput)
                .setPositiveButton("Continue", (dialog, which) -> {
                    String loginPhone = phoneInput.getText().toString().trim();
                    if (!loginPhone.isEmpty()) {
                        fraFlowData.phoneNumber = loginPhone; // Update with login phone
                        new android.os.Handler().postDelayed(() -> {
                            launchDigitalIdentitySDK(false); // false = login
                        }, 1000);
                    } else {
                        Toast.makeText(this, "Phone number is required", Toast.LENGTH_SHORT).show();
                        promptForLoginPhoneNumber(); // Retry
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    handleFRAFlowError("Login cancelled");
                })
                .setCancelable(false)
                .show();
    }

    private void launchDigitalIdentitySDK(boolean isRegistration) {
        debugLog("=== DIGITAL IDENTITY START ===");
        debugLog("Phone: " + fraFlowData.phoneNumber);
        debugLog("NID Transaction: " + fraFlowData.nidTransactionId);
        debugLog("About to generate token...");

        // First, generate token for Valify services
        tokenGenerator.generateAccessToken(
                USERNAME, PASSWORD, CLIENT_ID, CLIENT_SECRET, BASE_URL, this,
                new AccessTokenGenerator.AccessTokenCallback() {
                    @Override
                    public void onSuccess(AccessTokenResponse tokenResponse) {
                        debugLog("✓ Token generated successfully");
                        debugLog("Token present: " + (tokenResponse.getAccessToken() != null));


                            debugLog("Building ValifyConfig...");

                            HashMap<String, String> headers = createValifyHeaders();
                            debugLog("Headers created: " + headers.size() + " items");
                        // Build ValifyConfig (required)
                        ValifyConfig valifyConfig = new ValifyConfig.Builder()
                                .setBaseUrl(BASE_URL) // Valify base URL
                                .setBundleKey("5f0fd53872324f52afafb2fb668df379")
                                .setAccessToken(tokenResponse.getAccessToken())
                                .performCSO(true) // Enable CSO verification
                                .performNTRA(true) // Enable NTRA verification
                                .setPhoneNumber(fraFlowData.phoneNumber)
                                .build();

                            debugLog("✓ ValifyConfig built");

                            debugLog("Building AuthConfig...");

                        // Build Auth SDK config
                        VIDVAuthConfig.Builder authBuilder = new VIDVAuthConfig.Builder()
                                .setBaseUrl("https://digitalidentity.valifysolutions.com") // Digital Identity URL (different from Valify URL)
                                .setBundleKey("5f0fd53872324f52afafb2fb668df379")
                                .setUserReferenceId(generateUniqueUserId())
                                .setValifyConfigurations(valifyConfig) // Pass ValifyConfig
                                .setLanguage("en");
                                //.setAuthFlow(VIDVAuthFlow.ACCOUNT_CREATION);


                            debugLog("✓ AuthBuilder created");

                            // Set flow based on parameter
                        if (isRegistration) {
                            debugLog("Setting REGISTRATION flow");

                            authBuilder.setFlow(VIDVAuthService.registration());
                        } else {
                            debugLog("Setting LOGIN flow: " + fraFlowData.phoneNumber);

                            authBuilder.setFlow(VIDVAuthService.login(fraFlowData.phoneNumber));
                        }
                            debugLog("✓ Flow set");

                            VIDVAuthListener authListener = new VIDVAuthListener() {
                            @Override
                            public void onAuthResult(VIDVAuthResponse response) {
                                debugLog("✓ Auth result received: " + response.getClass().getSimpleName());

                                runOnUiThread(() -> {
                                    if (response instanceof VIDVAuthResponse.Success) {
                                        if (isRegistration) {
                                            debugLog("✓✓✓ SUCCESS!");

                                            completeFRAFlow();
                                        } else {
                                            resultText.append("\n✓ Login successful!\n");
                                            Toast.makeText(MainActivity.this,
                                                    "Login successful!",
                                                    Toast.LENGTH_LONG).show();
                                            //completeFRAFlow();
                                        }
                                    } else if (response instanceof VIDVAuthResponse.ServiceFailure) {
                                        VIDVAuthResponse.ServiceFailure failure = (VIDVAuthResponse.ServiceFailure) response;

                                        String action = isRegistration ? "registration" : "login";
                                        handleFRAFlowError("Digital identity " + action + " failed: " + failure.getMessage());
                                    } else if (response instanceof VIDVAuthResponse.BuilderError) {
                                        VIDVAuthResponse.BuilderError error = (VIDVAuthResponse.BuilderError) response;
                                        handleFRAFlowError("Builder error: " + error.getMessage());
                                    } else if (response instanceof VIDVAuthResponse.Exit) {
                                        String action = isRegistration ? "registration" : "login";
                                        handleFRAFlowError("User cancelled digital identity " + action);
                                    }
                                });
                            }
                        };

                        authBuilder.start(MainActivity.this, authListener);
                            debugLog("✓ Auth SDK start() called");

                        }

                    @Override
                    public void onFailure(Exception e) {
                        handleFRAFlowError("Token generation failed: " + e.getMessage());
                    }
                }
        );
    }


    // Helper method to create headers for Valify
    private HashMap<String, String> createValifyHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("X-Valify-reference-userid", generateUniqueUserId());
        return headers;
    }


    private void completeFRAFlow() {
        currentStep = FRAFlowStep.COMPLETE;

        StringBuilder summary = new StringBuilder();
        summary.append("\n\n=== FRA KYC FLOW COMPLETE ===\n\n");
        summary.append("✓ Phone Verified: ").append(fraFlowData.phoneNumber).append("\n");
        summary.append("✓ Email Verified: ").append(fraFlowData.email).append("\n");
        summary.append("✓ National ID Scanned\n");
        summary.append("✓ Biometrics Verified\n");
        summary.append("✓ NTRA Validated\n");
        summary.append("✓ CSO Submitted\n");
        summary.append("✓ Digital Identity Created\n\n");
        summary.append("All steps completed successfully!\n");
        summary.append("User is now fully verified and compliant with FRA regulations.");

        resultText.setText(summary.toString());

        new AlertDialog.Builder(this)
                .setTitle("✓ KYC Complete")
                .setMessage("Congratulations! Your FRA KYC verification is complete.\n\n" +
                        "You are now fully verified and compliant with Egyptian Financial Regulatory Authority requirements.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void handleFRAFlowError(String errorMessage) {
        runOnUiThread(() -> {
            resultText.append("\n\n✗ Error: " + errorMessage);

            new AlertDialog.Builder(this)
                    .setTitle("FRA Flow Error")
                    .setMessage("An error occurred during the KYC process:\n\n" + errorMessage + "\n\nWould you like to retry?")
                    .setPositiveButton("Retry", (dialog, which) -> {
                        startFRAFlow();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    // Helper method to extract NID from stored data
    private String extractNIDFromData() {
        if (fraFlowData.nidData.containsKey("nationalId")) {
            return fraFlowData.nidData.get("nationalId");
        } else if (fraFlowData.nidData.containsKey("national_id")) {
            return fraFlowData.nidData.get("national_id");
        } else if (fraFlowData.nidData.containsKey("front_nid")) {
            return fraFlowData.nidData.get("front_nid");
        }
        return "";
    }

    private String extractFirstNameFromData() {
        if (fraFlowData.nidData.containsKey("firstName")) {
            return fraFlowData.nidData.get("firstName");
        } else if (fraFlowData.nidData.containsKey("first_name")) {
            return fraFlowData.nidData.get("first_name");
        }
        return "";
    }

    private String extractFullNameFromData() {
        if (fraFlowData.nidData.containsKey("fullName")) {
            return fraFlowData.nidData.get("fullName");
        } else if (fraFlowData.nidData.containsKey("full_name")) {
            return fraFlowData.nidData.get("full_name");
        } else if (fraFlowData.nidData.containsKey("name")) {
            return fraFlowData.nidData.get("name");
        }
        return "";
    }

    private String extractSerialNumberFromData() {
        if (fraFlowData.nidData.containsKey("serialNumber")) {
            return fraFlowData.nidData.get("serialNumber");
        } else if (fraFlowData.nidData.containsKey("serial_number")) {
            return fraFlowData.nidData.get("serial_number");
        }
        return "";
    }

    private String extractExpirationFromData() {
        if (fraFlowData.nidData.containsKey("expiryDate")) {
            return fraFlowData.nidData.get("expiryDate");
        } else if (fraFlowData.nidData.containsKey("expiry_date")) {
            return fraFlowData.nidData.get("expiry_date");
        } else if (fraFlowData.nidData.containsKey("expiration")) {
            return fraFlowData.nidData.get("expiration");
        }
        return "";
    }

    // Generate a unique user ID for X-Valify-reference-userid header
    private String generateUniqueUserId() {
        // You can use the phone number or create a UUID
        // For this example, we'll create a simple ID based on phone
        return "USER_" + fraFlowData.phoneNumber.replaceAll("[^0-9]", "");
    }



    private void launchLoginSDK(String phoneNumber) {
        resultText.setText("Launching login for: " + phoneNumber);

        VIDVAuthConfig.Builder authBuilder = new VIDVAuthConfig.Builder()
                .setBaseUrl(BASE_URL)
                .setBundleKey("5f0fd53872324f52afafb2fb668df379")
                .setUserReferenceId(generateUniqueUserId())
                .setFlow(VIDVAuthService.login(phoneNumber)) // Login flow with phone number
                .setLanguage("en")
                .setPrimaryColor(Color.parseColor("#2196F3")); // Optional: Your brand color

        VIDVAuthListener authListener = new VIDVAuthListener() {
            @Override
            public void onAuthResult(VIDVAuthResponse response) {
                runOnUiThread(() -> {
                    if (response instanceof VIDVAuthResponse.Success) {
                        VIDVAuthResponse.Success success = (VIDVAuthResponse.Success) response;
                        resultText.setText("✓ Login Successful!\n\nWelcome back!");

                        Toast.makeText(MainActivity.this,
                                "Login successful!",
                                Toast.LENGTH_LONG).show();

                    } else if (response instanceof VIDVAuthResponse.ServiceFailure) {
                        VIDVAuthResponse.ServiceFailure failure = (VIDVAuthResponse.ServiceFailure) response;
                        resultText.setText("Login failed: " + failure.getMessage());

                    } else if (response instanceof VIDVAuthResponse.BuilderError) {
                        VIDVAuthResponse.BuilderError error = (VIDVAuthResponse.BuilderError) response;
                        resultText.setText("Builder error: " + error.getMessage());

                    } else if (response instanceof VIDVAuthResponse.Exit) {
                        VIDVAuthResponse.Exit exit = (VIDVAuthResponse.Exit) response;
                        resultText.setText("User cancelled login at: " + exit.getStep());
                    }
                });
            }
        };

        authBuilder.start(MainActivity.this, authListener);
    }

    private void generateTokenAndStart() {
        AccessTokenGenerator generator = new AccessTokenGenerator();
        generator.generateAccessToken(USERNAME,
                PASSWORD,
                CLIENT_ID,
                CLIENT_SECRET,
                BASE_URL,
                this,
                new AccessTokenGenerator.AccessTokenCallback() {
                    @Override
                    public void onSuccess(AccessTokenResponse tokenResponse) {
                        accessToken = tokenResponse.getAccessToken();
                        Toast.makeText(MainActivity.this, "Token ready!", Toast.LENGTH_SHORT).show();
                        startLivenessSDK();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(MainActivity.this, "Token failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void startLivenessSDK() {
        VIDVLivenessConfig.Builder builder = new VIDVLivenessConfig.Builder()
                .setBaseUrl(BASE_URL)
                .setAccessToken(accessToken)
                .setBundleKey("5f0fd53872324f52afafb2fb668df379")
                .setNumberOfInstructions(3)
                .setLanguage("en")
                .setInstructionTimer(10)
                .showErrorDialogs(true);
        //.setFrontTransactionId(frontTansactionId);





        VIDVLivenessListener listener = new VIDVLivenessListener() {
            @Override
            public void onLivenessResult(VIDVLivenessResponse response) {
                runOnUiThread(() -> {
                    if (response instanceof Success) {
                        Toast.makeText(MainActivity.this, "Liveness Passed!", Toast.LENGTH_SHORT).show();
                    } else if (response instanceof BuilderError) {
                        Toast.makeText(MainActivity.this, "Builder Error: " +
                                ((BuilderError) response).errorMessage, Toast.LENGTH_LONG).show();
                    } else if (response instanceof ServiceFailure) {
                        Toast.makeText(MainActivity.this, "Failed: " +
                                ((ServiceFailure) response).errorMessage, Toast.LENGTH_LONG).show();
                    } else if (response instanceof UserExit) {
                        Toast.makeText(MainActivity.this, "User exited", Toast.LENGTH_SHORT).show();
                    } else if (response instanceof CapturedActions) {
                        Toast.makeText(MainActivity.this, "Capturing...", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        };

        builder.start(MainActivity.this, listener);
    }

    private void callSanctionApi(String accessToken) {
        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject requestBody = new JSONObject();
            JSONArray sanctionListsArray = new JSONArray();
            JSONArray fieldsArray = new JSONArray();

            requestBody.put("bundle_key", "5f0fd53872324f52afafb2fb668df379");

            sanctionListsArray.put("MLCU");
            requestBody.put("sanction_lists", sanctionListsArray);

            // Add document_number field
            JSONObject documentNumberField = new JSONObject();
            documentNumberField.put("field_name", "document_number");
            documentNumberField.put("value", "30006060104118");
            documentNumberField.put("cutoff_match_score", 0.8);
            fieldsArray.put(documentNumberField);

            // Add full_name field
            JSONObject fullNameField = new JSONObject();
            fullNameField.put("field_name", "full_name");
            fullNameField.put("value", "Kareem Nader");
            fullNameField.put("cutoff_match_score", 0.8);
            fieldsArray.put(fullNameField);

            requestBody.put("fields", fieldsArray);
            requestBody.put("match_all_fields", true);
            requestBody.put("transliterate", false);


            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(SANCTION_API_URL)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> resultText.setText("Request failed: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();

                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String formattedResult = formatSanctionResponse(jsonResponse);
                            resultText.setText(formattedResult);
                        } catch (JSONException e) {
                            resultText.setText("Error parsing response: " + e.getMessage());
                        }
                    });
                }
            });

        } catch (Exception e) {
            runOnUiThread(() -> resultText.setText("JSON error: " + e.getMessage()));
        }
    }

    private String formatSanctionResponse(JSONObject jsonResponse) throws JSONException {
        StringBuilder result = new StringBuilder();

        result.append("Transaction ID: ").append(jsonResponse.getString("transaction_id")).append("\n");
        result.append("Trials Remaining: ").append(jsonResponse.getInt("trials_remaining")).append("\n\n");

        JSONObject resultObj = jsonResponse.getJSONObject("result");
        result.append("Status: ").append(resultObj.getString("status")).append("\n");
        result.append("Is Match: ").append(resultObj.getBoolean("is_match")).append("\n\n");

        if (resultObj.getBoolean("is_match")) {
            JSONArray matchedRecords = resultObj.getJSONArray("matched_records");
            result.append("Matched Records:\n");

            for (int i = 0; i < matchedRecords.length(); i++) {
                JSONObject record = matchedRecords.getJSONObject(i);
                JSONObject data = record.getJSONObject("data");

                result.append("\n--- Record ").append(i + 1).append(" ---\n");
                result.append("Full Name: ").append(data.optString("full_name", "N/A")).append("\n");
                result.append("Document Number: ").append(data.optString("document_number", "N/A")).append("\n");
                result.append("Language: ").append(data.optString("language", "N/A")).append("\n");
                result.append("Remarks: ").append(data.optString("remarks", "N/A")).append("\n");
                result.append("Sanction List: ").append(record.getString("sanction_list")).append("\n\n");

                result.append("Matched Fields:\n");
                JSONArray matchedFields = record.getJSONArray("matched_fields");
                for (int j = 0; j < matchedFields.length(); j++) {
                    JSONObject field = matchedFields.getJSONObject(j);
                    result.append("  - Field: ").append(field.getString("field_name"))
                            .append(" | Match Score: ").append(field.getDouble("match_score")).append("\n");
                }
            }
        } else {
            result.append("No matches found in the sanction lists.");
        }

        return result.toString();
    }

    private void generateTokenAndStartSDK() {
        tokenGenerator.generateAccessToken(
                USERNAME,
                PASSWORD,
                CLIENT_ID,
                CLIENT_SECRET,
                BASE_URL,
                this,
                new AccessTokenGenerator.AccessTokenCallback() {
                    @Override
                    public void onSuccess(AccessTokenResponse tokenResponse) {
                        startDockit(tokenResponse.getAccessToken());
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(MainActivity.this, "Token Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void startDockit(String accessToken) {
        VIDVDocKitConfig.Builder docKitBuilder = new VIDVDocKitConfig.Builder()
                .setBaseUrl("https://www.valifystage.com/")
                .setAccessToken(accessToken)
                .setBundleKey("5f0fd53872324f52afafb2fb668df379")
                .setDocumentType(VIDVDocKitDocType.egyNID())
                .setLanguage("en")
                .previewCapturedImage(true)
                //.setCaptureMode(new VIDVCaptureMode.AUTO_AFTER(5));
                //.setCaptureMode(VIDVCaptureMode.MANUAL.INSTANCE);
                .setCaptureMode(VIDVCaptureMode.AUTOMATIC.INSTANCE);

        docKitBuilder.start(this, new VIDVDocKitListener() {
            @Override
            public void onDocKitResult(VIDVDocKitResponse result) {
                if (result instanceof VIDVDocKitResponse.Success) {
                    VIDVDocKitResponse.Success success = (VIDVDocKitResponse.Success) result;
                    VIDVDocKitData data = success.getData();

                    // Save the session ID
                    frontTransactionId = data.getSessionID();
                    startLivenessSDK();

                    // Extract and store the scanned data
                    extractDocKitData(success);

                    // Show dialog asking if user wants to transliterate
                    runOnUiThread(() -> showTransliterationDialog());

                } else if (result instanceof VIDVDocKitResponse.ServiceFailure) {
                    VIDVDocKitResponse.ServiceFailure fail = (VIDVDocKitResponse.ServiceFailure) result;
                    Toast.makeText(MainActivity.this,
                            "Service Failure: " + fail.getMessage(), Toast.LENGTH_LONG).show();

                } else if (result instanceof VIDVDocKitResponse.BuilderError) {
                    VIDVDocKitResponse.BuilderError err = (VIDVDocKitResponse.BuilderError) result;
                    Toast.makeText(MainActivity.this,
                            "Builder Error: " + err.getMessage(), Toast.LENGTH_LONG).show();

                } else if (result instanceof VIDVDocKitResponse.Exit) {
                    VIDVDocKitResponse.Exit exit = (VIDVDocKitResponse.Exit) result;
                    String step = exit.getStep();
                    Toast.makeText(MainActivity.this,
                            "User exited at step: " + step, Toast.LENGTH_LONG).show();
                }
            }
        });
    }


    private void extractDocKitData(VIDVDocKitResponse.Success success) {
        // Clear previous data
        lastScannedData.clear();

        try {
            // Get the data object from DocKit
            VIDVDocKitData data = success.getData();


            if (data != null) {
                // Get the extracted data map
                Map<String, Object> extractedData = data.getExtractedData();

                if (extractedData != null && !extractedData.isEmpty()) {
                    Log.d("DocKit", "Raw extracted data: " + extractedData.toString());

                    // Check if data is nested under "result" key
                    if (extractedData.containsKey("result")) {
                        Object resultObj = extractedData.get("result");

                        // If result is a Map, use it directly
                        if (resultObj instanceof Map) {
                            Map<String, Object> resultMap = (Map<String, Object>) resultObj;
                            for (Map.Entry<String, Object> entry : resultMap.entrySet()) {
                                if (entry.getValue() != null && !entry.getValue().toString().trim().isEmpty()) {
                                    lastScannedData.put(entry.getKey(), entry.getValue().toString());
                                }
                            }
                        } else {
                            // If result is a string, try to parse it (shouldn't happen but just in case)
                            Log.e("DocKit", "Result is not a Map, it's: " + resultObj.getClass().getName());
                        }
                    } else {
                        // Data is not nested, use it directly
                        for (Map.Entry<String, Object> entry : extractedData.entrySet()) {
                            if (entry.getValue() != null && !entry.getValue().toString().trim().isEmpty()) {
                                lastScannedData.put(entry.getKey(), entry.getValue().toString());
                            }
                        }
                    }

                    // Show the transliterate button
                    runOnUiThread(() -> transliterateButton.setVisibility(Button.VISIBLE));

                    Log.d("DocKit", "Processed data for transliteration: " + lastScannedData.toString());

                    Toast.makeText(MainActivity.this,
                            "Scan successful! " + lastScannedData.size() + " fields extracted",
                            Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e("DocKit", "Error extracting data: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void showSanctionCheckDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Sanction Check Available")
                .setMessage("Would you like to check this person against sanction lists?")
                .setPositiveButton("Yes", (dialog, which) -> showSanctionConfigurationDialog())
                .setNegativeButton("No", (dialog, which) -> {
                    Toast.makeText(MainActivity.this,
                            "You can check sanctions later using the 'Check Sanction' button",
                            Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }

    private void showSanctionConfigurationDialog() {
        // Create a custom dialog layout
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_sanction_config, null);

        // Get references to all UI elements
        android.widget.CheckBox cbMLCU = dialogView.findViewById(R.id.cbMLCU);
        android.widget.CheckBox cbOFAC = dialogView.findViewById(R.id.cbOFAC);
        android.widget.CheckBox cbUN = dialogView.findViewById(R.id.cbUN);
        android.widget.CheckBox cbEU = dialogView.findViewById(R.id.cbEU);

        android.widget.CheckBox cbUseDocumentNumber = dialogView.findViewById(R.id.cbUseDocumentNumber);
        android.widget.EditText etDocumentNumber = dialogView.findViewById(R.id.etDocumentNumber);
        android.widget.EditText etDocumentCutoff = dialogView.findViewById(R.id.etDocumentCutoff);

        android.widget.CheckBox cbUseFullName = dialogView.findViewById(R.id.cbUseFullName);
        android.widget.EditText etFullName = dialogView.findViewById(R.id.etFullName);
        android.widget.EditText etNameCutoff = dialogView.findViewById(R.id.etNameCutoff);

        android.widget.CheckBox cbMatchAllFields = dialogView.findViewById(R.id.cbMatchAllFields);
        android.widget.CheckBox cbTransliterate = dialogView.findViewById(R.id.cbTransliterate);

        // Pre-fill with extracted data if available
        String documentNumber = extractDocumentNumber();
        String fullName = extractFullName();

        if (documentNumber != null && !documentNumber.isEmpty()) {
            cbUseDocumentNumber.setChecked(true);
            etDocumentNumber.setText(documentNumber);
            etDocumentNumber.setEnabled(true);
            etDocumentCutoff.setEnabled(true);
        }

        if (fullName != null && !fullName.isEmpty()) {
            cbUseFullName.setChecked(true);
            etFullName.setText(fullName);
            etFullName.setEnabled(true);
            etNameCutoff.setEnabled(true);
        }

        // Set default cutoff scores
        etDocumentCutoff.setText("0.8");
        etNameCutoff.setText("0.8");

        // Default: MLCU checked
        cbMLCU.setChecked(true);

        // Enable/disable text fields based on checkboxes
        cbUseDocumentNumber.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etDocumentNumber.setEnabled(isChecked);
            etDocumentCutoff.setEnabled(isChecked);
        });

        cbUseFullName.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etFullName.setEnabled(isChecked);
            etNameCutoff.setEnabled(isChecked);
        });

        // Create and show dialog
        new AlertDialog.Builder(this)
                .setTitle("Configure Sanction Check")
                .setView(dialogView)
                .setPositiveButton("Check Sanctions", (dialog, which) -> {
                    // Collect selected sanction lists
                    java.util.ArrayList<String> selectedLists = new java.util.ArrayList<>();
                    if (cbMLCU.isChecked()) selectedLists.add("MLCU");
                    if (cbOFAC.isChecked()) selectedLists.add("OFAC");
                    if (cbUN.isChecked()) selectedLists.add("UN_Sanctions");
                    if (cbEU.isChecked()) selectedLists.add("EU_Sanctions");

                    if (selectedLists.isEmpty()) {
                        Toast.makeText(this, "Please select at least one sanction list", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Collect field data
                    SanctionCheckConfig config = new SanctionCheckConfig();
                    config.sanctionLists = selectedLists;
                    config.matchAllFields = cbMatchAllFields.isChecked();
                    config.transliterate = cbTransliterate.isChecked();

                    if (cbUseDocumentNumber.isChecked()) {
                        String docNum = etDocumentNumber.getText().toString().trim();
                        String docCutoff = etDocumentCutoff.getText().toString().trim();

                        if (!docNum.isEmpty() && !docCutoff.isEmpty()) {
                            try {
                                config.useDocumentNumber = true;
                                config.documentNumber = docNum;
                                config.documentCutoff = Double.parseDouble(docCutoff);
                            } catch (NumberFormatException e) {
                                Toast.makeText(this, "Invalid document cutoff score", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                    }

                    if (cbUseFullName.isChecked()) {
                        String name = etFullName.getText().toString().trim();
                        String nameCutoff = etNameCutoff.getText().toString().trim();

                        if (!name.isEmpty() && !nameCutoff.isEmpty()) {
                            try {
                                config.useFullName = true;
                                config.fullName = name;
                                config.nameCutoff = Double.parseDouble(nameCutoff);
                            } catch (NumberFormatException e) {
                                Toast.makeText(this, "Invalid name cutoff score", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                    }

                    if (!config.useDocumentNumber && !config.useFullName) {
                        Toast.makeText(this, "Please select at least one field to search", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Proceed with sanction check
                    generateTokenAndCheckSanction(config);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Helper methods to extract data
    private String extractDocumentNumber() {
        if (lastScannedData.containsKey("nationalId")) {
            return lastScannedData.get("nationalId");
        } else if (lastScannedData.containsKey("national_id")) {
            return lastScannedData.get("national_id");
        } else if (lastScannedData.containsKey("front_nid")) {
            return lastScannedData.get("front_nid");
        }
        return "";
    }

    private String extractFullName() {
        if (lastScannedData.containsKey("fullName")) {
            return lastScannedData.get("fullName");
        } else if (lastScannedData.containsKey("full_name")) {
            return lastScannedData.get("full_name");
        } else if (lastScannedData.containsKey("name")) {
            return lastScannedData.get("name");
        } else if (lastScannedData.containsKey("firstName")) {
            return lastScannedData.get("firstName");
        }
        return "";
    }

    // Configuration class to hold user selections
    private static class SanctionCheckConfig {
        java.util.ArrayList<String> sanctionLists;
        boolean useDocumentNumber = false;
        String documentNumber;
        double documentCutoff;
        boolean useFullName = false;
        String fullName;
        double nameCutoff;
        boolean matchAllFields = false;
        boolean transliterate = false;
    }

    private void showTransliterationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Transliteration Available")
                .setMessage("Would you like to transliterate the scanned Arabic text to English?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    generateTokenAndTransliterate();
                    // After transliteration, show sanction check dialog
                    new android.os.Handler().postDelayed(() -> {
                        runOnUiThread(() -> showSanctionCheckDialog());
                    }, 1000); // Wait 1 second before showing next dialog
                })
                .setNegativeButton("No", (dialog, which) -> {
                    // Skip transliteration but still offer sanction check
                    showSanctionCheckDialog();
                })
                .setCancelable(false)
                .show();
    }

    private void generateTokenAndCheckSanction(SanctionCheckConfig config) {
        resultText.setText("Checking sanctions...");

        tokenGenerator.generateAccessToken(
                USERNAME,
                PASSWORD,
                CLIENT_ID,
                CLIENT_SECRET,
                BASE_URL,
                this,
                new AccessTokenGenerator.AccessTokenCallback() {
                    @Override
                    public void onSuccess(AccessTokenResponse tokenResponse) {
                        callSanctionApiWithConfig(tokenResponse.getAccessToken(), config);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        resultText.setText("Token generation failed: " + e.getMessage());
                    }
                }
        );
    }


    private void callSanctionApiWithConfig(String accessToken, SanctionCheckConfig config) {
        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject requestBody = new JSONObject();
            JSONArray sanctionListsArray = new JSONArray();
            JSONArray fieldsArray = new JSONArray();

            requestBody.put("bundle_key", "5f0fd53872324f52afafb2fb668df379");

            // Add selected sanction lists
            for (String list : config.sanctionLists) {
                sanctionListsArray.put(list);
            }
            requestBody.put("sanction_lists", sanctionListsArray);

            // Add document_number field if selected
            if (config.useDocumentNumber) {
                JSONObject documentNumberField = new JSONObject();
                documentNumberField.put("field_name", "document_number");
                documentNumberField.put("value", config.documentNumber);
                documentNumberField.put("cutoff_match_score", config.documentCutoff);
                fieldsArray.put(documentNumberField);
                Log.d("Sanction", "Adding document_number: " + config.documentNumber + " (cutoff: " + config.documentCutoff + ")");
            }

            // Add full_name field if selected
            if (config.useFullName) {
                JSONObject fullNameField = new JSONObject();
                fullNameField.put("field_name", "full_name");
                fullNameField.put("value", config.fullName);
                fullNameField.put("cutoff_match_score", config.nameCutoff);
                fieldsArray.put(fullNameField);
                Log.d("Sanction", "Adding full_name: " + config.fullName + " (cutoff: " + config.nameCutoff + ")");
            }

            requestBody.put("fields", fieldsArray);
            requestBody.put("match_all_fields", config.matchAllFields);
            requestBody.put("transliterate", config.transliterate);

            Log.d("Sanction", "Request body: " + requestBody.toString());

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(SANCTION_API_URL)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        resultText.setText("Sanction check request failed: " + e.getMessage());
                        Log.e("Sanction", "Request failed", e);
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();

                    Log.d("Sanction", "Response code: " + response.code());
                    Log.d("Sanction", "Response body: " + responseBody);

                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);

                            if (jsonResponse.has("message") && !jsonResponse.has("result")) {
                                resultText.setText("API Error: " + jsonResponse.getString("message"));
                                return;
                            }

                            String formattedResult = formatSanctionResponse(jsonResponse);
                            resultText.setText(formattedResult);

                            JSONObject resultObj = jsonResponse.getJSONObject("result");
                            if (resultObj.getBoolean("is_match")) {
                                showSanctionMatchAlert();
                            }

                        } catch (JSONException e) {
                            resultText.setText("Error parsing response: " + e.getMessage() + "\n\nRaw response:\n" + responseBody);
                            Log.e("Sanction", "Parse error", e);
                        }
                    });
                }
            });

        } catch (Exception e) {
            runOnUiThread(() -> {
                resultText.setText("Sanction check error: " + e.getMessage());
                Log.e("Sanction", "Error creating request", e);
            });
        }
    }

    private void showSanctionMatchAlert() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Sanction Match Found")
                .setMessage("This person appears on one or more sanction lists. Please review the details carefully.")
                .setPositiveButton("OK", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }



    private void generateTokenAndTransliterate() {
        resultText.setText("Transliterating...");

        tokenGenerator.generateAccessToken(
                USERNAME,
                PASSWORD,
                CLIENT_ID,
                CLIENT_SECRET,
                BASE_URL,
                this,
                new AccessTokenGenerator.AccessTokenCallback() {
                    @Override
                    public void onSuccess(AccessTokenResponse tokenResponse) {
                        callTransliterationApi(tokenResponse.getAccessToken());
                    }

                    @Override
                    public void onFailure(Exception e) {
                        resultText.setText("Token generation failed: " + e.getMessage());
                    }
                }
        );
    }

    private void callTransliterationApi(String accessToken) {
        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("bundle_key", "5f0fd53872324f52afafb2fb668df379");

            // Create data object with mapped fields
            JSONObject dataObject = new JSONObject();

            // Map DocKit field names to API expected field names
            Map<String, String> fieldMapping = new HashMap<>();
            fieldMapping.put("firstName", "first_name");
            fieldMapping.put("first_name", "first_name");
            fieldMapping.put("fullName", "full_name");
            fieldMapping.put("full_name", "full_name");
            fieldMapping.put("name", "full_name");
            fieldMapping.put("address", "street");
            fieldMapping.put("street", "street");
            fieldMapping.put("area", "area");
            fieldMapping.put("nationalId", "front_nid");
            fieldMapping.put("national_id", "front_nid");
            fieldMapping.put("front_nid", "front_nid");
            fieldMapping.put("serialNumber", "serial_number");
            fieldMapping.put("serial_number", "serial_number");
            fieldMapping.put("backNid", "back_nid");
            fieldMapping.put("back_nid", "back_nid");
            fieldMapping.put("expiryDate", "expiry_date");
            fieldMapping.put("expiry_date", "expiry_date");
            fieldMapping.put("releaseDate", "release_date");
            fieldMapping.put("release_date", "release_date");
            fieldMapping.put("dateOfBirth", "date_of_birth");
            fieldMapping.put("date_of_birth", "date_of_birth");
            fieldMapping.put("birthDate", "date_of_birth");
            fieldMapping.put("gender", "gender");
            fieldMapping.put("profession", "profession");
            fieldMapping.put("religion", "religion");
            fieldMapping.put("husbandName", "husband_name");
            fieldMapping.put("husband_name", "husband_name");
            fieldMapping.put("maritalStatus", "marital_status");
            fieldMapping.put("marital_status", "marital_status");

            // Log the data we're sending
            Log.d("Transliteration", "Original scanned data: " + lastScannedData.toString());

            // Map and add fields
            for (Map.Entry<String, String> entry : lastScannedData.entrySet()) {
                String originalKey = entry.getKey();
                String value = entry.getValue();

                // Only add non-empty values
                if (value != null && !value.trim().isEmpty()) {
                    // Check if we have a mapping for this field
                    String mappedKey = fieldMapping.get(originalKey);

                    if (mappedKey != null) {
                        dataObject.put(mappedKey, value);
                        Log.d("Transliteration", "Mapped: " + originalKey + " -> " + mappedKey + " = " + value);
                    } else {
                        // Try adding with original key anyway
                        dataObject.put(originalKey, value);
                        Log.d("Transliteration", "Using original key: " + originalKey + " = " + value);
                    }
                }
            }

            // Check if we have at least one valid field
            if (dataObject.length() == 0) {
                runOnUiThread(() -> {
                    resultText.setText("Error: No valid fields extracted from scan.\n\nExtracted fields: " + lastScannedData.keySet().toString());
                });
                return;
            }

            requestBody.put("data", dataObject);

            // Log the mapped request
            Log.d("Transliteration", "Mapped request body: " + requestBody.toString());

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(TRANSLITERATION_API_URL)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        resultText.setText("Transliteration request failed: " + e.getMessage());
                        Log.e("Transliteration", "Request failed", e);
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();

                    // Log the raw response
                    Log.d("Transliteration", "Response code: " + response.code());
                    Log.d("Transliteration", "Response body: " + responseBody);

                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);

                            // Check if there's an error message
                            if (jsonResponse.has("message") && jsonResponse.has("errors")) {
                                resultText.setText("API Error: " + jsonResponse.getString("message") +
                                        "\n\nDetails: " + jsonResponse.getJSONObject("errors").toString());
                                return;
                            }

                            // Check if result exists
                            if (!jsonResponse.has("result") || jsonResponse.isNull("result")) {
                                resultText.setText("Error: No transliteration result returned.\n\nRaw response:\n" + responseBody);
                                return;
                            }

                            String formattedResult = formatTransliterationResponse(jsonResponse);
                            resultText.setText(formattedResult);

                        } catch (JSONException e) {
                            resultText.setText("Error parsing response: " + e.getMessage() + "\n\nRaw response:\n" + responseBody);
                            Log.e("Transliteration", "Parse error", e);
                        }
                    });
                }
            });

        } catch (Exception e) {
            runOnUiThread(() -> {
                resultText.setText("Transliteration error: " + e.getMessage());
                Log.e("Transliteration", "Error creating request", e);
            });
        }
    }

    private String formatTransliterationResponse(JSONObject jsonResponse) throws JSONException {
        StringBuilder result = new StringBuilder();

        result.append("=== TRANSLITERATION RESULTS ===\n\n");
        result.append("Transaction ID: ").append(jsonResponse.optString("transaction_id", "N/A")).append("\n");
        result.append("Trials Remaining: ").append(jsonResponse.optInt("trials_remaining", 0)).append("\n\n");

        JSONObject resultObj = jsonResponse.optJSONObject("result");
        if (resultObj != null) {
            result.append("--- Transliterated Data ---\n\n");

            // Display all transliterated fields
            if (resultObj.has("first_name")) {
                result.append("First Name: ").append(resultObj.getString("first_name")).append("\n");
            }
            if (resultObj.has("full_name")) {
                result.append("Full Name: ").append(resultObj.getString("full_name")).append("\n");
            }
            if (resultObj.has("date_of_birth")) {
                result.append("Date of Birth: ").append(resultObj.getString("date_of_birth")).append("\n");
            }
            if (resultObj.has("age")) {
                result.append("Age: ").append(resultObj.getString("age")).append("\n");
            }
            if (resultObj.has("gender")) {
                result.append("Gender: ").append(resultObj.getString("gender")).append("\n");
            }
            if (resultObj.has("religion")) {
                result.append("Religion: ").append(resultObj.getString("religion")).append("\n");
            }
            if (resultObj.has("marital_status")) {
                result.append("Marital Status: ").append(resultObj.getString("marital_status")).append("\n");
            }
            if (resultObj.has("husband_name")) {
                result.append("Husband Name: ").append(resultObj.getString("husband_name")).append("\n");
            }
            if (resultObj.has("profession")) {
                result.append("Profession: ").append(resultObj.getString("profession")).append("\n");
            }
            if (resultObj.has("front_nid")) {
                result.append("Front NID: ").append(resultObj.getString("front_nid")).append("\n");
            }
            if (resultObj.has("back_nid")) {
                result.append("Back NID: ").append(resultObj.getString("back_nid")).append("\n");
            }
            if (resultObj.has("serial_number")) {
                result.append("Serial Number: ").append(resultObj.getString("serial_number")).append("\n");
            }
            if (resultObj.has("street")) {
                result.append("Street: ").append(resultObj.getString("street")).append("\n");
            }
            if (resultObj.has("area")) {
                result.append("Area: ").append(resultObj.getString("area")).append("\n");
            }
            if (resultObj.has("police_station")) {
                result.append("Police Station: ").append(resultObj.getString("police_station")).append("\n");
            }
            if (resultObj.has("governorate")) {
                result.append("Governorate: ").append(resultObj.getString("governorate")).append("\n");
            }
            if (resultObj.has("birth_governorate")) {
                result.append("Birth Governorate: ").append(resultObj.getString("birth_governorate")).append("\n");
            }
            if (resultObj.has("release_date")) {
                result.append("Release Date: ").append(resultObj.getString("release_date")).append("\n");
            }
            if (resultObj.has("expiry_date")) {
                result.append("Expiry Date: ").append(resultObj.getString("expiry_date")).append("\n");
            }
        }

        return result.toString();
    }
}
