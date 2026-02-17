package com.example.dockit_test;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AccessTokenGenerator {

    private OkHttpClient client;

    public AccessTokenGenerator() {
        client = new OkHttpClient();
    }

    // Method to generate the access token
    public void generateAccessToken(String username, String password, String clientId, String clientSecret, String baseUrl, Context context, AccessTokenCallback callback) {
        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "password")
                .build();

        Request request = new Request.Builder()
                .url(baseUrl+"/api/o/token/")  // Ensure the correct URL is used
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Handle failure
                ((MainActivity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Request failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    callback.onFailure(e);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    Log.d("API Response", responseBody);

                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        String accessToken = jsonObject.getString("access_token");
                        int expiresIn = jsonObject.getInt("expires_in");
                        String tokenType = jsonObject.getString("token_type");
                        String scope = jsonObject.getString("scope");
                        String refreshToken = jsonObject.getString("refresh_token");

                        // Create AccessTokenResponse instance and set values
                        AccessTokenResponse tokenResponse = new AccessTokenResponse();
                        tokenResponse.setAccessToken(accessToken);
                        tokenResponse.setExpiresIn(expiresIn);
                        tokenResponse.setTokenType(tokenType);
                        tokenResponse.setScope(scope);
                        tokenResponse.setRefreshToken(refreshToken);

                        // Call success callback with the generated token response
                        ((MainActivity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "Access token generated", Toast.LENGTH_SHORT).show();
                            callback.onSuccess(tokenResponse);
                        });

                    } catch (JSONException e) {
                        Log.e("JSON Parsing Error", e.getMessage());
                        ((MainActivity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "Failed to parse JSON response", Toast.LENGTH_SHORT).show();
                            callback.onFailure(e);
                        });
                    }
                } else {
                    // Handle the error response
                    ((MainActivity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "Request failed: " + response.message(), Toast.LENGTH_SHORT).show();
                        callback.onFailure(new IOException("Unexpected code " + response));
                    });
                }
            }
        });
    }

    // Interface for handling the result of the access token generation
    public interface AccessTokenCallback {
        void onSuccess(AccessTokenResponse tokenResponse);
        void onFailure(Exception e);
    }
}