package com.example.dockit_test;

public class AccessTokenResponse {
    private String access_token;
    private int expires_in;
    private String token_type;
    private String scope;
    private String refresh_token;

    // Getter for access_token
    public String getAccessToken() {
        return access_token;
    }

    // Setter for access_token
    public void setAccessToken(String access_token) {
        this.access_token = access_token;
    }

    // Getter for expires_in
    public int getExpiresIn() {
        return expires_in;
    }

    // Setter for expires_in
    public void setExpiresIn(int expires_in) {
        this.expires_in = expires_in;
    }

    // Getter for token_type
    public String getTokenType() {
        return token_type;
    }

    // Setter for token_type
    public void setTokenType(String token_type) {
        this.token_type = token_type;
    }

    // Getter for scope
    public String getScope() {
        return scope;
    }

    // Setter for scope
    public void setScope(String scope) {
        this.scope = scope;
    }

    // Getter for refresh_token
    public String getRefreshToken() {
        return refresh_token;
    }

    // Setter for refresh_token
    public void setRefreshToken(String refresh_token) {
        this.refresh_token = refresh_token;
    }
}
