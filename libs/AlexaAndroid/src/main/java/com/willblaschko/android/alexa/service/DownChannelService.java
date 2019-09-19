package com.willblaschko.android.alexa.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.amazon.identity.auth.device.AuthError;
import com.amazon.identity.auth.device.api.Listener;
import com.amazon.identity.auth.device.api.authorization.AuthorizationManager;
import com.amazon.identity.auth.device.api.authorization.AuthorizeResult;
import com.amazon.identity.auth.device.api.authorization.ProfileScope;
import com.amazon.identity.auth.device.api.authorization.Scope;
import com.willblaschko.android.alexa.AlexaManager;
import com.willblaschko.android.alexa.TokenManager;
import com.willblaschko.android.alexa.callbacks.ImplAsyncCallback;
import com.willblaschko.android.alexa.connection.ClientUtil;
import com.willblaschko.android.alexa.data.Directive;
import com.willblaschko.android.alexa.data.Event;
import com.willblaschko.android.alexa.interfaces.AvsItem;
import com.willblaschko.android.alexa.interfaces.AvsResponse;
import com.willblaschko.android.alexa.interfaces.response.ResponseParser;
import com.willblaschko.android.alexa.system.AndroidSystemHandler;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;

/**
 * @author will on 4/27/2016.
 */
public class DownChannelService extends Service {

    private static final String TAG = "DownChannelService";

    private AlexaManager alexaManager;
    private Call currentCall;
    private AndroidSystemHandler handler;
    private Handler runnableHandler;
    private Runnable pingRunnable;
    private Scope[] scopes;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Launched");
        alexaManager = AlexaManager.getInstance(this);
        handler = AndroidSystemHandler.getInstance(this);
        scopes = new Scope[]{ProfileScope.profile(), ProfileScope.postalCode()};

        runnableHandler = new Handler(Looper.getMainLooper());
        pingRunnable = new Runnable() {
            @Override
            public void run() {
                AuthorizationManager.getToken(DownChannelService.this, scopes, new Listener<AuthorizeResult, AuthError>() {

                    @Override
                    public void onSuccess(AuthorizeResult authorizeResult) {
                        Log.i(TAG, "Sending heartbeat");
                        final Request request = new Request.Builder()
                                .url(alexaManager.getPingUrl())
                                .get()
                                .addHeader("Authorization", "Bearer " + authorizeResult.getAccessToken())
                                .build();

                        ClientUtil.getTLS12OkHttpClient()
                                .newCall(request)
                                .enqueue(new Callback() {
                                    @Override
                                    public void onFailure(Call call, IOException e) {

                                    }

                                    @Override
                                    public void onResponse(Call call, Response response) throws IOException {
                                        runnableHandler.postDelayed(pingRunnable, 4 * 60 * 1000);
                                    }
                                });
                    }

                    @Override
                    public void onError(AuthError authError) {

                    }
                });
            }
        };
        
        openDownChannel();

    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if(currentCall != null){
            currentCall.cancel();
        }
        runnableHandler.removeCallbacks(pingRunnable);
    }


    private void openDownChannel(){

        AuthorizationManager.getToken(DownChannelService.this, scopes, new Listener<AuthorizeResult, AuthError>() {
            @Override
            public void onSuccess(AuthorizeResult authorizeResult) {

                OkHttpClient downChannelClient = ClientUtil.getTLS12OkHttpClient();

                final Request request = new Request.Builder()
                        .url(alexaManager.getDirectivesUrl())
                        .get()
                        .addHeader("Authorization", "Bearer " + authorizeResult.getAccessToken())
                        .build();

                currentCall = downChannelClient.newCall(request);
                currentCall.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {

                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {

                        if (TokenManager.doesTokenExists(DownChannelService.this)) {
                            alexaManager.sendEvent(Event.getSynchronizeStateEvent(), new ImplAsyncCallback<AvsResponse, Exception>() {
                                @Override
                                public void success(AvsResponse result) {
                                    handler.handleItems(result);
                                    runnableHandler.post(pingRunnable);
                                }
                            });
                        }

                        BufferedSource bufferedSource = response.body().source();

                        while (!bufferedSource.exhausted()) {
                            String line = bufferedSource.readUtf8Line();
                            try {
                                Directive directive = ResponseParser.getDirective(line);
                                handler.handleDirective(directive);

                                //surface to our UI if it's up
                                try {
                                    AvsItem item = ResponseParser.parseDirective(directive);
                                    EventBus.getDefault().post(item);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Bad line");
                            }
                        }

                    }
                });
            }

            @Override
            public void onError(AuthError authError) {
                authError.printStackTrace();
            }
        });
    }

}
