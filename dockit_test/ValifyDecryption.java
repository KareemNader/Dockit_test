package com.example.dockit_test;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.json.JSONObject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;

public class ValifyDecryption {

    private static final String TAG = "ValifyDecryption";
    private PrivateKey privateKey;
    private Context context;

    // Callback interface for async results
    public interface DecryptionCallback {
        void onSuccess(JSONObject decryptedData);
        void onFailure(String error);
    }

    /**
     * Constructor - initializes with your private key
     */
    public ValifyDecryption(Context context, String privateKeyStr) throws Exception {
        this.context = context;
        this.privateKey = loadPrivateKey(privateKeyStr);
    }

    /**
     * Query transaction by Transaction ID
     */
    public void queryTransaction(String baseUrl, String accessToken, String bundleKey,
                                 String transactionId, DecryptionCallback callback) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("bundle_key", bundleKey);
            requestBody.put("transaction_id", transactionId);

            makeApiCall(baseUrl, accessToken, requestBody, callback);

        } catch (Exception e) {
            callback.onFailure("Error creating request: " + e.getMessage());
        }
    }

    /**
     * Query all transactions by Access Token
     */
    public void queryByAccessToken(String baseUrl, String accessToken, String bundleKey,
                                   String queryAccessToken, DecryptionCallback callback) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("bundle_key", bundleKey);
            requestBody.put("access_token", queryAccessToken);

            makeApiCall(baseUrl, accessToken, requestBody, callback);

        } catch (Exception e) {
            callback.onFailure("Error creating request: " + e.getMessage());
        }
    }

    /**
     * Make the API call using OkHttp (same as your other API calls)
     */
    private void makeApiCall(String baseUrl, String accessToken, JSONObject requestBody,
                             DecryptionCallback callback) {
        OkHttpClient client = new OkHttpClient();

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                okhttp3.MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/transaction/inquire/")
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("API request failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();

                try {
                    JSONObject encryptedResponse = new JSONObject(responseBody);
                    JSONObject decryptedData = decryptResponse(encryptedResponse);
                    callback.onSuccess(decryptedData);

                } catch (Exception e) {
                    callback.onFailure("Decryption failed: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Decrypt the API response
     */
    private JSONObject decryptResponse(JSONObject response) throws Exception {
        // Get encrypted values
        String encryptedDataB64 = response.getString("data");
        String encryptedKeyB64 = response.getString("key");
        String ivB64 = response.getString("iv");

        // Decode from base64
        byte[] encryptedData = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            encryptedData = Base64.getDecoder().decode(encryptedDataB64);
        }
        byte[] encryptedKey = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            encryptedKey = Base64.getDecoder().decode(encryptedKeyB64);
        }
        byte[] iv = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            iv = Base64.getDecoder().decode(ivB64);
        }

        // Decrypt AES key with RSA
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] aesKey = rsaCipher.doFinal(encryptedKey);

        // Decrypt data with AES
        Cipher aesCipher = Cipher.getInstance("AES/CBC/NoPadding");
        aesCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
        byte[] decryptedBytes = aesCipher.doFinal(encryptedData);

        // Remove padding
        int padLength = decryptedBytes[decryptedBytes.length - 1];
        byte[] unpadded = new byte[decryptedBytes.length - padLength];
        System.arraycopy(decryptedBytes, 0, unpadded, 0, unpadded.length);

        // Convert to JSON
        String jsonStr = new String(unpadded);
        return new JSONObject(jsonStr);
    }

    /**
     * Load private key from string
     */
    private PrivateKey loadPrivateKey(String keyStr) throws Exception {
        String key = keyStr
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyBytes = Base64.getDecoder().decode(key);
        }
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}
