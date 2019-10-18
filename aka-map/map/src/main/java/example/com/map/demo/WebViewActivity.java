package example.com.map.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.akamai.android.sdk.AkaMap;
import com.akamai.android.sdk.util.AnaUtils;

import java.util.ArrayList;
import android.graphics.Bitmap;
import android.os.Build;
import com.akamai.android.sdk.VocService;
import com.akamai.android.sdk.net.webkit.AkaWebViewL15Client;
import com.akamai.android.sdk.net.webkit.AkaWebViewL21Client;

public class WebViewActivity extends Activity {

    private String mUrl;
    private WebView mWebView;
    private long mStartTime, mStopTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        mUrl = getIntent().getStringExtra("url");
        AkaMap.getInstance().logEvent("Webview Started.");
        start();
    }

    private void start() {
        mWebView = (WebView) findViewById(R.id.webView);
        mWebView.clearCache(true);
        mWebView.clearHistory();
        mWebView.getSettings().setAppCacheEnabled(false);
        mWebView.getSettings().setLoadWithOverviewMode(true);
        mWebView.getSettings().setUseWideViewPort(true);
        mWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

        final Context context = this;
        if (Build.VERSION.SDK_INT >= 21) {
            //mWebView.setWebViewClient(new AkaWebViewL21Client());
            mWebView.setWebViewClient(new AkaWebViewL21Client() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    mStartTime = System.currentTimeMillis();
                    super.onPageStarted(view, url, favicon);
                    // mUrl loading started.
                    AkaMap.getInstance().startEvent(mUrl);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    mStopTime = System.currentTimeMillis();
                    super.onPageFinished(view, url);
                    // mUrl loading finished.
                    AkaMap.getInstance().stopEvent(mUrl);
                    try {
                        showStats(context, url);
                    } catch (Exception e) {}
                }
            });
        } else {
            mWebView.setWebViewClient(new AkaWebViewL15Client() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    mStartTime = System.currentTimeMillis();
                    super.onPageStarted(view, url, favicon);
                    // mUrl loading started.
                    AkaMap.getInstance().startEvent(mUrl);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    mStopTime = System.currentTimeMillis();
                    super.onPageFinished(view, url);
                    // mUrl loading finished.
                    AkaMap.getInstance().stopEvent(mUrl);
                    try {
                        showStats(context, url);
                    } catch (Exception e) {}
                }
            });
        }
        mWebView.loadUrl(mUrl);

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    // TODO - Below sample is for demo purposes only. This is not part of public API and should not be used as such.
    private void showStats(Context context, String url) {
        long webViewDuration = mStopTime - mStartTime;
        new AlertDialog.Builder(context)
                .setTitle("Time: " + webViewDuration + " ms")
                .setIcon(R.mipmap.ic_launcher)
                .show();
    }

    private String getConnectionType(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo wifiInfo = AnaUtils.getNetworkInfo(connManager, ConnectivityManager.TYPE_WIFI);
        if (wifiInfo != null && wifiInfo.isConnected()) {
            return "wifi";
        }
        NetworkInfo cellInfo = AnaUtils.getNetworkInfo(connManager, ConnectivityManager.TYPE_MOBILE);
        if (cellInfo != null && cellInfo.isConnected()) {
            if (!TextUtils.isEmpty(cellInfo.getSubtypeName())) {
                return "cellular/"+cellInfo.getSubtypeName();
            }
            return "cellular";
        }
        return "";
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
}

