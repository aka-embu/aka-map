package example.com.map.demo;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.akamai.android.sdk.AkaCommon;
import com.akamai.android.sdk.Logger;
import com.akamai.android.sdk.AkaMap;
import com.akamai.android.sdk.MapConfigBuilder;
import com.akamai.android.sdk.MapNetworkQualityStatus;
import com.akamai.android.sdk.MapSdkInfo;
import com.akamai.android.sdk.net.AkaURLStreamHandler;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.HEAD;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Created by vaanand on 10/6/15.
 */
public class InitActivity extends Activity {

    private AkaMap mMapInstance;
    public static String url = "https://www.akamai.com";
    private static final String LOG_TAG = "InitActivity";
    private Picasso mPicasso;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize Accelerator
        mMapInstance = AkaMap.getInstance();
        setContentView(R.layout.activity_init);
        setActionBar();
        AkaCommon.getInstance().getContext();
        Logger.setLevel(Logger.LEVEL.DEBUG);
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .build());
    }


    private void setActionBar() {
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        final ImageView homeButton = (ImageView) findViewById(android.R.id.home);
        homeButton.setBackgroundColor(getResources().getColor(android.R.color.white));
        View customActionBarView = getLayoutInflater().inflate(R.layout.action_bar, null);
        actionBar.setCustomView(customActionBarView);
    }

    public void onClickRegisterUpdate(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        showSegmentDialog();
    }

    private void showSegmentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Segments");

        // Setup the input area. For padding purpose, setup a framelayout and add the textview.
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final TextView desc = new TextView(this);
        desc.setText("Enter the list of segments separated by comma");
        desc.setLayoutParams(params);
        container.addView(desc);
        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        try {
            input.setText(MapSdkInfo.getSegments());
        } catch (Exception e) {
            e.printStackTrace();
        }
        container.addView(input);
        builder.setView(container);

        // Set up the buttons
        builder.setPositiveButton("SUBMIT", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String segments = input.getText().toString();
                mMapInstance.subscribeSegments(new HashSet<>(Arrays.asList(segments)));
                dialog.cancel();
            }
        });
        builder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.cancel();
            }
        });

         builder.show();
    }

    public void onClickGET(View view) {
        // HttpURLConnection usage. Make sure to call HttpURLConnection#disconnect() to release resources and collect stats.
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        final StringBuilder sb = new StringBuilder();
        final String uri = url;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection urlConnection = null;
                try {
                    URL url = new URL(null, uri);
                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setConnectTimeout(10000);
                    urlConnection.setReadTimeout(10000);
                    // Set request parameters if needed
                    // urlConnection.setRequestProperty("User-Agent", getPackageName());
                    //Get response headers if needed
                    Map<String, List<String>> responseHeaders = urlConnection.getHeaderFields();
                    // Get response code if needed
                    int responseCode = urlConnection.getResponseCode();
                    // Download content if needed
                    InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
                    byte[] readBuffer = new byte[8192];
                    int bytesRead;
                    int total = 0;
                    while ((bytesRead = inputStream.read(readBuffer)) != -1)
                    {
                        //Save readBuffer if needed!
                        total += bytesRead;
                    }
                    // Close the stream once done with the download.
                    inputStream.close();

                    sb.append("Response Code: ");
                    sb.append(responseCode);
                    sb.append(" | ");
                    sb.append("Downloaded: ");
                    sb.append(total/1024);
                    sb.append(" KB");
                } catch (IOException e) {
                    e.printStackTrace();
                    sb.append(e);
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                    }
                }

            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        new AlertDialog.Builder(this)
                .setTitle(uri)
                .setMessage(sb.toString())
                .setIcon(R.mipmap.ic_launcher)
                .show();

    }

    public void onClickGETWithAkaStreamHandler(View view) {
        // HttpURLConnection usage. Make sure to call HttpURLConnection#disconnect() to release resources and collect stats.
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        final StringBuilder sb = new StringBuilder();
        final String uri = url;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection urlConnection = null;
                try {
                    URL url = new URL(null, uri, new AkaURLStreamHandler());
                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setConnectTimeout(10000);
                    urlConnection.setReadTimeout(10000);
                    // Set request parameters if needed
                    // urlConnection.setRequestProperty("User-Agent", getPackageName());
                    //Get response headers if needed
                    Map<String, List<String>> responseHeaders = urlConnection.getHeaderFields();
                    // Get response code if needed
                    int responseCode = urlConnection.getResponseCode();
                    // Download content if needed
                    InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
                    byte[] readBuffer = new byte[8192];
                    int bytesRead;
                    int total = 0;
                    while ((bytesRead = inputStream.read(readBuffer)) != -1)
                    {
                        //Save readBuffer if needed!
                        total += bytesRead;
                    }
                    // Close the stream once done with the download.
                    inputStream.close();

                    sb.append("Response Code: ");
                    sb.append(responseCode);
                    sb.append(" | ");
                    sb.append("Downloaded: ");
                    sb.append(total/1024);
                    sb.append(" KB");
                } catch (IOException e) {
                    e.printStackTrace();
                    sb.append(e);
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                    }
                }

            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        new AlertDialog.Builder(this)
                .setTitle(uri)
                .setMessage(sb.toString())
                .setIcon(R.mipmap.ic_launcher)
                .show();

    }

    public void onClickPOST(View view) {
        // HttpURLConnection usage. Make sure to call HttpURLConnection#disconnect() to release resources and collect stats.
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        final StringBuilder sb = new StringBuilder();
        /* POST URL */
        final String uri = "https://postman-echo.com/post";
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection urlConnection = null;
                try {
                    URL url = new URL(null, uri);
                    urlConnection = (HttpURLConnection) url.openConnection();
                    // set request method
                    urlConnection.setRequestMethod("POST");
                    // set request headers if any
                    urlConnection.setRequestProperty("x-hdr-1", "val1");
                    urlConnection.setRequestProperty("x-hdr-2", "val2");
                    // set do output to true.
                    urlConnection.setDoOutput(true);
                    // Set the appropriate content type for the request body.
                    urlConnection.setRequestProperty("Content-Type", "application/json");
                    urlConnection.setConnectTimeout(10000);
                    urlConnection.setReadTimeout(10000);
                    // prepare POST data
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("fName", "First");
                    jsonObject.put("lName", "Last");
                    jsonObject.put("age", 11);
                    jsonObject.put("ts", System.currentTimeMillis());
                    byte []postData = jsonObject.toString().getBytes();
                    int contentLength = postData.length;
                    urlConnection.setFixedLengthStreamingMode(contentLength);

                    // Now write post data.
                    OutputStream outputStream = urlConnection.getOutputStream();
                    outputStream.write(postData);
                    outputStream.flush();
                    outputStream.close();
                    // Read server response once request is sent.
                    urlConnection.connect();
                    // Get response code if needed
                    int responseCode = urlConnection.getResponseCode();
                    //Get response headers if needed
                    Map<String, List<String>> responseHeaders = urlConnection.getHeaderFields();

                    // Download content if needed
                    InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
                    byte[] readBuffer = new byte[8192];
                    int bytesRead;
                    int total = 0;
                    while ((bytesRead = inputStream.read(readBuffer)) != -1)
                    {
                        //Save readBuffer if needed!
                        total += bytesRead;
                    }
                    // Close the stream once done with the download.
                    inputStream.close();
                    sb.append("Response Code: ");
                    sb.append(responseCode);
                    sb.append(" | ");
                    sb.append("Downloaded: ");
                    sb.append(total);
                    sb.append(" bytes");
                } catch (Exception e) {
                    e.printStackTrace();
                    sb.append(e);
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                    }
                }

            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        new AlertDialog.Builder(this)
                .setTitle(uri)
                .setMessage(sb.toString())
                .setIcon(R.mipmap.ic_launcher)
                .show();
    }

    public void onClickOkhttpWithAppInterceptor(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {

                OkHttpClient client = new OkHttpClient.Builder()
                        .addInterceptor(new AkaOkHttpAppInterceptor())
                        .build();

                Request request = new Request.Builder()
                        .url("https://www.akamai.com/us/en/products/performance/web-performance-optimization.jsp")
                        .header("User-Agent", "OkHttp Example")
                        .build();

                Response response = null;
                try {
                    response = client.newCall(request).execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                response.body().close();
            }

        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void onClickOkhttpWithInterceptor(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {

                OkHttpClient client = new OkHttpClient.Builder()
                        .addInterceptor(new AkaOkHttpInterceptor())
                        .build();

                Request request = new Request.Builder()
                        .url("https://www.akamai.com/us/en/about/news/press/2017-press/akamai-adds-intelligent-performance-automation-and-mobile-app-optimization-to-ion.jsp")
                        .header("User-Agent", "OkHttp Example")
                        .build();

                Response response = null;
                try {
                    response = client.newCall(request).execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                response.body().close();
            }

        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public interface Retrofit2Api {
        @GET("/{responseType}")
        public Call<ResponseBody> get(@Path("responseType") String responseType);

        @POST("/{responseType}")
        @FormUrlEncoded
        Call<ResponseBody> post(@Path("responseType") String responseType, @Field("body") String body);

        @DELETE("/{responseType}")
        public Call<ResponseBody> delete(@Path("responseType") String responseType);

        @HEAD("/{responseType}")
        public Call<Void> head(@Path("responseType") String responseType);
    }

    public void onClickRetrofit2(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {

                Retrofit retrofit = new Retrofit.Builder()
                        .baseUrl("https://www.akamai.com/")
                        .addConverterFactory(GsonConverterFactory.create())
                        .client(AkaRetrofit2Client.getClient())
                        .build();
                Retrofit2Api client =  retrofit.create(Retrofit2Api.class);
                Call<ResponseBody> call = client.get("us/en/about/news/press/2017-press/akamai-adds-intelligent-performance-automation-and-mobile-app-optimization-to-ion.jsp");
                try {
                    retrofit2.Response<ResponseBody> response = call.execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void onClickPicasso(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                if (mPicasso == null) {
                    mPicasso = new Picasso.Builder(getApplicationContext())
                            .downloader(new AkaPicassoDownloader(getApplicationContext()))
                            .build();
                    Picasso.setSingletonInstance(mPicasso);
                }
                RequestCreator creator = Picasso.with(getApplicationContext()).load("https://www.akamai.com/us/en/multimedia/images/custom/threat-research/hero--banner.jpg?imwidth=1366");
                creator.fetch(new com.squareup.picasso.Callback() {
                    @Override
                    public void onSuccess() {
                        Logger.dd("Picasso Image Downloaded");
                    }
                    @Override
                    public void onError() {
                        Logger.dd("Picasso Image Download failed");
                    }
                });
            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void onClickWebView(View view) {
        Intent intent = new Intent(this, WebViewActivity.class);
        Button button = (Button) view;
        if (view.getId() == R.id.bestbuy) {
            mMapInstance.logEvent("Clicked " + button.getText());
            intent.putExtra("url", url);
        }
        startActivity(intent);
    }
    
    public void onClickBlacklistGet(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        Set blacklist  = new HashSet<String>();
        blacklist.add(url);

        MapConfigBuilder builder = new MapConfigBuilder(getApplicationContext());
        builder.setBlacklist(blacklist);
    }

    public void onClickWhitelistGet(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        Set whitelist  = new HashSet<String>();
        whitelist.add(url);

        MapConfigBuilder builder = new MapConfigBuilder(getApplicationContext());
        builder.setWhitelist(whitelist);
    }

    public void onClickSet200MBMem(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        Set whitelist  = new HashSet<String>();
        whitelist.add(url);

        MapConfigBuilder builder = new MapConfigBuilder(getApplicationContext());
        builder.sdkCacheLimit(200); //1 MB
    }

    public void onClickClearRestrictiononGet(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        Set whitelist  = new HashSet<String>();
        MapConfigBuilder builder = new MapConfigBuilder(getApplicationContext());
        builder.setBlacklist(null);
        builder.setWhitelist(null);
    }

    public void onClickCongestion(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... params) {
                try {
                    final int state = mMapInstance.getNetworkQuality();
                    final String stringVal;
                    switch (state) {
                        case MapNetworkQualityStatus.POOR:
                            stringVal = "Poor";
                            break;
                        case MapNetworkQualityStatus.GOOD:
                            stringVal = "Good";
                            break;
                        case MapNetworkQualityStatus.EXCELLENT:
                            stringVal = "Excellent";
                            break;
                        default:
                            stringVal = "N/A";
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String msg = "Network Quality: " + stringVal;
                            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                        }
                    });
                }catch (Exception e){}
                return null;
            }

            protected void onPostExecute(Void result) {
            }

        }.execute();
    }


    public void onClickAllSubSeg(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        try {
            Toast.makeText(getApplicationContext(), MapSdkInfo.getSegments(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "IllegalStateException, check logcat", Toast.LENGTH_SHORT).show();
        }
    }

    public void onClickLogConfig(View view) {
        try {
            MapSdkInfo.logCurrentConfiguration(getApplicationContext());
            Toast.makeText(getApplicationContext(), "Success, check logcat", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "IllegalStateException, check logcat", Toast.LENGTH_SHORT).show();
        }
    }

    public void onClickLogContent(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        try {
            MapSdkInfo.logExistingContent(getApplicationContext());
            Toast.makeText(getApplicationContext(), "Success, check logcat", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "IllegalStateException, check logcat", Toast.LENGTH_SHORT).show();
        }
    }

    public void onClickIsEnabled(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        try {
            Toast.makeText(getApplicationContext(), MapSdkInfo.isEnabled()+"", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "IllegalStateException, check logcat", Toast.LENGTH_SHORT).show();
        }
    }

    public void onClickDebugMode(View view) {
        Button button = (Button) view;
        mMapInstance.logEvent("Clicked " + button.getText());
        // Toggles debug mode.
        // NOTE - This is only for testing and verifying stuff. Make sure the level is NOT set to DEBUG for release builds.
        // It may incur performance implications.
        Logger.LEVEL currentLevel = Logger.getCurrentLogLevel();
        if (currentLevel == Logger.LEVEL.INFO) {
            Logger.setLevel(Logger.LEVEL.DEBUG);
            Toast.makeText(getApplicationContext(), "Log level set to DEBUG", Toast.LENGTH_SHORT).show();
        } else {
            Logger.setLevel(Logger.LEVEL.INFO);
            Toast.makeText(getApplicationContext(), "Log level set to INFO", Toast.LENGTH_SHORT).show();
        }
    }

}
