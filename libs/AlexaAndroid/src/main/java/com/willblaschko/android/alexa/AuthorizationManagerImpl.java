package com.willblaschko.android.alexa;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.amazon.identity.auth.device.AuthError;
import com.amazon.identity.auth.device.api.Listener;
import com.amazon.identity.auth.device.api.authorization.AuthCancellation;
import com.amazon.identity.auth.device.api.authorization.AuthorizationManager;
import com.amazon.identity.auth.device.api.authorization.AuthorizeListener;
import com.amazon.identity.auth.device.api.authorization.AuthorizeRequest;
import com.amazon.identity.auth.device.api.authorization.AuthorizeResult;
import com.amazon.identity.auth.device.api.authorization.ProfileScope;
import com.amazon.identity.auth.device.api.authorization.Scope;
import com.amazon.identity.auth.device.api.authorization.ScopeFactory;
import com.amazon.identity.auth.device.api.workflow.RequestContext;
import com.amazon.identity.auth.device.authorization.api.AmazonAuthorizationManager;
import com.amazon.identity.auth.device.authorization.api.AuthorizationListener;
import com.amazon.identity.auth.device.authorization.api.AuthzConstants;
import com.amazon.identity.auth.device.interactive.InteractiveListener;
import com.amazon.identity.auth.device.interactive.InteractiveRequestRecord;
import com.amazon.identity.auth.device.workflow.WorkflowCancellation;
import com.willblaschko.android.alexa.callbacks.AsyncCallback;
import com.willblaschko.android.alexa.callbacks.AuthorizationCallback;
import com.willblaschko.android.alexa.utility.Util;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

/**
 * Created by Maxime on 17/09/2019.
 */
public class AuthorizationManagerImpl {
    private Context context;
    private String productId;
    private Scope[] scopes;
    private RequestContext requestContext;
    private AuthorizationCallback callback;

    public AuthorizationManagerImpl(@NonNull Context context, @NonNull String productId) {
        this.context = context;
        this.productId = productId;
        this.scopes = new Scope[]{ProfileScope.profile(), ProfileScope.postalCode()};
        requestContext = RequestContext.create(context);
    }

    /**
     *
     * @param context
     * @param callback
     */
    public void checkLoggedIn(Context context, final AsyncCallback<Boolean, Throwable> callback) {
        AuthorizationManager.getToken(context, scopes, new Listener<AuthorizeResult, AuthError>() {
            @Override
            public void onSuccess(AuthorizeResult authorizeResult) {
                callback.success(authorizeResult.getAccessToken() != null);
            }

            @Override
            public void onError(AuthError authError) {
                callback.failure(authError.getCause());
            }
        });
    }

    public void getToken(Context context, final TokenManager.TokenCallback callback) {
        AuthorizationManager.getToken(context, scopes, new Listener<AuthorizeResult, AuthError>() {
            @Override
            public void onSuccess(AuthorizeResult authorizeResult) {
                callback.onSuccess(authorizeResult.getAccessToken());
            }

            @Override
            public void onError(AuthError authError) {
                callback.onFailure(authError);
            }
        });
    }

    public void authorizeUser(AuthorizationCallback callback) {
        final JSONObject scopeData = new JSONObject();
        final JSONObject productInstanceAttributes = new JSONObject();
        this.callback = callback;

        try {
            String PRODUCT_DSN = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            productInstanceAttributes.put("deviceSerialNumber", PRODUCT_DSN);
            scopeData.put("productInstanceAttributes", productInstanceAttributes);
            scopeData.put("productID", productId);
            requestContext.registerListener(getAuthorizeListener(callback));
            requestContext.onResume();
            AuthorizationManager.authorize(new AuthorizeRequest.Builder(requestContext).addScopes(ProfileScope.profile(), ProfileScope.postalCode()).build());
        } catch (JSONException e) {
            e.printStackTrace();
            callback.onError(e);
        }
    }

    private AuthorizeListener getAuthorizeListener(final AuthorizationCallback callback) {
        return new AuthorizeListener() {
            @Override
            public void onSuccess(AuthorizeResult authorizeResult) {
                callback.onSuccess();
            }

            @Override
            public void onError(AuthError authError) {
                callback.onError(new Exception(authError));
            }

            @Override
            public void onCancel(AuthCancellation authCancellation) {
                callback.onCancel();
            }
        };
    }

    /**
     * Create a new code verifier for our token exchanges
     * @return the new code verifier
     */
    static String createCodeVerifier() {
        return createCodeVerifier(128);
    }

    /**
     * Create a new code verifier for our token exchanges
     * @return the new code verifier
     */
    private static String createCodeVerifier(int count) {
        char[] chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890".toCharArray();
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            char c = chars[random.nextInt(chars.length)];
            sb.append(c);
        }
        return sb.toString();
    }

}
