package example.com.map.testapp;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.akamai.android.sdk.Logger;
import com.akamai.android.sdk.MapSdkInfo;
import com.akamai.android.sdk.net.AkaHttpStatsCollector;
import com.akamai.android.sdk.net.AkaHttpUtils;
import com.akamai.android.sdk.net.AkaUrlStat;
import com.akamai.android.sdk.net.AkaUrlStatCollector;
import com.akamai.android.sdk.util.ErrorUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;


public class AkaOkHttpAppInterceptor implements Interceptor {

    private static final String CONTENT_LENGTH_HEADER = "Content-Length";
    private static final int CHUNK_SIZE = 4096;

    @Override
    public Response intercept(Chain chain) throws IOException {

        if (AkaHttpUtils.isContentCached(chain.request().url().toString())) {
            return getResponse(chain.request());
        }
        final String amcHeaderValue = AkaHttpUtils.getAMCHeaderString(false);
        final String arHeaderValue = AkaHttpUtils.getARHeaderString();
        final String url = AkaHttpUtils.getAkaUrl(chain.request().url().url(),
                chain.request().method());
        final Request request;
        if (!TextUtils.isEmpty(amcHeaderValue) && !TextUtils.isEmpty(arHeaderValue) &&
                !TextUtils.isEmpty(url)) {
            request = chain.request().newBuilder()
                    .url(url)
                    .addHeader(AkaHttpUtils.getAMCHeaderName(), amcHeaderValue)
                    .addHeader(AkaHttpUtils.getARHeaderName(), arHeaderValue)
                    .build();

        } else {
            request = chain.request();
        }

        final long startTime = getCurrentUTCTimeInMillis();
        AkaUrlStat info = new AkaUrlStat();
        info.mUrl = chain.request().url().url();
        info.mStartTime = startTime;
        AkaUrlStatCollector.getInstance().urlReqStart(request, info);

        Response response = null;
        URL urlObj = chain.request().url().url();
        //catch the client side exceptions and log the stats
        try
        {
            response = chain.proceed(request);
        }
        catch (IOException e)
        {
            logStats(startTime,0, getCurrentUTCTimeInMillis(),
                    0, urlObj, 0, e.getMessage(), e, request, response, 0);
            throw e;
        }
        catch (Exception e)
        {
            Logger.dd("Error - " + e);
        }

        if (!MapSdkInfo.isEnabled()){
            return response;
        }
        if (response == null)
        {
            logStats(startTime,0, getCurrentUTCTimeInMillis(),
                    0, urlObj, 0, "Response is null", null, request, response, 0);
        }
        final long connectTime = getCurrentUTCTimeInMillis();

        if (response.body() != null && response.body().byteStream() != null) {
            AkaResponseBody responseBody = new AkaResponseBody(response.body(),
                    startTime, connectTime, request);
            Response wrappedResponse = new Response.Builder()
                    .request(response.request())
                    .protocol(response.protocol())
                    .code(response.code())
                    .message(response.message())
                    .handshake(response.handshake())
                    .headers(response.headers())
                    .body(responseBody)
                    .networkResponse(response.networkResponse())
                    .cacheResponse(response.cacheResponse())
                    .priorResponse(response.priorResponse())
                    .sentRequestAtMillis(response.sentRequestAtMillis())
                    .receivedResponseAtMillis(response.receivedResponseAtMillis())
                    .build();
            responseBody.setResponse(wrappedResponse);
            return wrappedResponse;
        }  else {
            String errorPhrase = response.message();
            if(response.code() > 399){
                errorPhrase =  "Server error code: " + response.code() + " with error message: " + response.message();
            }
            logStats(startTime, 0, getCurrentUTCTimeInMillis(), 0, response.request().url().url(), response.code(), errorPhrase, null, request, response, connectTime);

            return response;
        }
    }

    private void logStats(long startTime, long connectTime, long ttfb,
                          long endTime, long bytesRead, Response response,
                          Exception exception, Request request) {

        logStats(startTime, ttfb, endTime, bytesRead, response.request().url().url(), response.code(), response.message(), exception, request, response, connectTime);
    }

    private void logStats(long startTime, long ttfb,
                          long endTime, long bytesRead, URL urlobj, int responseCode, String errorPhrase,
                          Exception exception, Request request, Response response, long connectTime)
    {
        long reqTime = (endTime > startTime && startTime > 0) ? endTime - startTime : 0;
        long ttfbCorrected = (ttfb > startTime && startTime > 0) ? ttfb - startTime : 0;
        AkaUrlStat info = new AkaUrlStat();
        info.mUrl = urlobj;
        info.mContentSize = bytesRead;
        info.mResponseCode = responseCode;
        info.mTtfb = (int) ttfbCorrected;
        info.mTimeStamp = new Date(startTime);
        info.mDuration = (int) reqTime;
        info.mException = exception;
        info.mReasonPhrase = errorPhrase;
        AkaUrlStatCollector.getInstance().urlReqEnd(request, info);


        String url = response == null ? urlobj.toString() : AkaHttpUtils.getUrl(response.request().url().toString());
        AkaHttpStatsCollector stats = new AkaHttpStatsCollector(url);
        int cacheable = -1;
        if (response != null) {
            // Request/Response specific parameters
            boolean isCachedResponse = response.cacheResponse() != null;
            String type;
            if (isCachedResponse) {
                type = AkaHttpStatsCollector.TYPE_USER_CACHED_ADHOC;
            } else {
                type = AkaHttpStatsCollector.TYPE_USER_NETWORK;
            }
            stats.insert(AkaHttpStatsCollector.KEY_TYPE, type);
            stats.insert(AkaHttpStatsCollector.KEY_MIME_TYPE, getContentTypeForLogging(response));
            stats.insert(AkaHttpStatsCollector.KEY_RESPONSECODE, (responseCode < 0) ? 0 : responseCode);
            stats.insert(AkaHttpStatsCollector.KEY_REQUEST_SIZE, getRequestSize(response.request()));
            stats.insert(AkaHttpStatsCollector.KEY_RESPONSE_HEADER_SIZE,
                    AkaHttpUtils.getHeaderSize(getResponseHeadersForLogging(response.headers().toMultimap())));
            if(!isCachedResponse) {
                cacheable = AkaHttpUtils.isResponseCacheable(response.headers().toMultimap()) ? 1 : 0;
            }
            stats.insert(AkaHttpStatsCollector.KEY_REQUEST_TYPE, response.request().method());
            stats.insert(AkaHttpStatsCollector.KEY_SENTREQUEST, response.sentRequestAtMillis());
            stats.insert(AkaHttpStatsCollector.KEY_RCVDRESPONSE, response.receivedResponseAtMillis());
            stats.insert(AkaHttpStatsCollector.KEY_REASONPHRASE, response.message());
            stats.insert(AkaHttpStatsCollector.KEY_REDIRECTED, response.isRedirect()?"true":"false");
        } else {
            stats.insert(AkaHttpStatsCollector.KEY_TYPE, "ERROR");
            stats.insert(AkaHttpStatsCollector.KEY_MIME_TYPE, "ERROR");
            stats.insert(AkaHttpStatsCollector.KEY_RESPONSECODE, 0);
            stats.insert(AkaHttpStatsCollector.KEY_REQUEST_SIZE, 0l);
            stats.insert(AkaHttpStatsCollector.KEY_RESPONSE_HEADER_SIZE,
                   0l);
            stats.insert(AkaHttpStatsCollector.KEY_REQUEST_TYPE, request.method());
            stats.insert(AkaHttpStatsCollector.KEY_SENTREQUEST, 0l);
            stats.insert(AkaHttpStatsCollector.KEY_RCVDRESPONSE, 0l);
            stats.insert(AkaHttpStatsCollector.KEY_REASONPHRASE, errorPhrase);
            stats.insert(AkaHttpStatsCollector.KEY_REDIRECTED, "false");
        }
        stats.insert(AkaHttpStatsCollector.KEY_CONTENT_LENGTH, bytesRead);
        stats.insert(AkaHttpStatsCollector.KEY_START_TIME, startTime);
        stats.insert(AkaHttpStatsCollector.KEY_DURATION, reqTime);
        stats.insert(AkaHttpStatsCollector.KEY_TTFB, ttfbCorrected);


        stats.insert(AkaHttpStatsCollector.KEY_CACHEABLE, cacheable);

        stats.insert(AkaHttpStatsCollector.KEY_EXCEPTION_MESSAGE,
                exception == null ? "" : ErrorUtils.getError(exception));

        stats.insert(AkaHttpStatsCollector.KEY_READSTARTTIME, ttfb);
        stats.insert(AkaHttpStatsCollector.KEY_CONNECTTIME, connectTime);


        // Server specifics
        String serverProfile = getHeaderField(response, AkaHttpUtils.getAMCHeaderName());
        String amcId = getHeaderField(response, AkaHttpUtils.getAMCIDHeaderName());
        String contentLengthHeader = getHeaderField(response, CONTENT_LENGTH_HEADER);
        stats.insert(AkaHttpStatsCollector.KEY_SERVER_PROFILE, serverProfile);
        stats.insert(AkaHttpStatsCollector.KEY_AMC_ID, amcId);
        stats.insert(AkaHttpStatsCollector.KEY_CONTENT_LENGTH_HEADER, contentLengthHeader);

        // Insert the stats
        AkaHttpUtils.updateHttpStats(stats);
        Logger.dd("Stats logged -" + stats);
    }

    private long getRequestSize(Request request) {
        long size = 0L;
        try {
            RequestBody requestBody = request.body();
            if (requestBody != null && requestBody.contentLength() != -1) {
                size += requestBody.contentLength();
            }
            Headers headers = request.headers();
            for (int i = 0, count = headers.size(); i < count; i++) {
                String name = headers.name(i);
                String value = headers.value(i);
                size += name.getBytes().length + value.getBytes().length;
            }
        } catch (Exception e) {
        }
        return size;
    }

    private String getContentTypeForLogging(Response response) {
        try {
            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                return responseBody.contentType().toString();
            }
        } catch (Exception e) {
        }
        return "";
    }

    private String getHeaderField(Response response, String headerKey) {
        String headerValue = "";
        try {
            headerValue =  response.header(headerKey);
            if (headerValue == null) {
                headerValue = "";
            }
        } catch (Exception ignored) {}
        return headerValue;
    }

    private Map<String, String> getResponseHeadersForLogging(Map<String,List<String>> headers) {
        Map<String, String> ret = new HashMap<>();
        try {
            ret = AkaHttpUtils.convertToWebViewHeaders(headers, false);
        } catch (Exception e) {}
        return ret;
    }

    private static long getCurrentUTCTimeInMillis() {
        return Calendar.getInstance(TimeZone.getTimeZone("utc")).getTimeInMillis();
    }

    private class AkaResponseBody extends ResponseBody {
        private ResponseBody mBody;
        private Response mResponse;
        private long mStartTime;
        private long mConnectTime;
        private long mTTFB;
        private long mEndTime;
        private Exception mException;
        private boolean mReadComplete;
        private boolean mReadStart;
        private long mBytesRead = 0L;
        private InputStream is;
        private Request mRequest;

        private AkaResponseBody(ResponseBody body, long startTime, long connectTime, Request request) {
            mBody = body;
            mStartTime = startTime;
            mConnectTime = connectTime;
            is = new WrappedInputStream(mBody.byteStream());
            mRequest = request;
        }

        @Override
        public MediaType contentType() {
            return mBody.contentType();
        }

        @Override
        public long contentLength() {
            return mBody.contentLength();
        }

        @Override
        public BufferedSource source() {
            return Okio.buffer(Okio.source(is));
        }

        @Override
        public void close() {
            mBody.close();
            onReadComplete();
        }

        private void onReadComplete() {
            if (!mReadComplete) {
                mReadComplete = true;
                mEndTime = getCurrentUTCTimeInMillis();
                logStatsAsync();
            }
        }

        private void setResponse(Response response) {
            mResponse = response;
        }

        private void logStatsAsync() {
            // On a separate thread
            AkaHttpUtils.submitTask(new Runnable() {
                @Override
                public void run() {
                    logStats(mStartTime, mConnectTime, mTTFB, mEndTime, mBytesRead, mResponse,
                            mException, mRequest);
                }
            });
        }

        private class WrappedInputStream extends InputStream {

            private InputStream mInputStream;

            public WrappedInputStream(InputStream inputStream) {
                mInputStream = inputStream;
            }

            @Override
            public int read() throws IOException {
                try {
                    int ret = mInputStream.read();
                    if (ret == -1) {
                        onReadComplete();
                    } else {
                        ++mBytesRead;
                        onReadStart();
                    }
                    return ret;
                } catch (IOException e) {
                    updateException(e);
                    throw e;
                }
            }


            @Override
            public int available() throws IOException {
                try {
                    return mInputStream.available();
                } catch (IOException e) {
                    updateException(e);
                    throw e;
                }
            }

            @Override
            public void close() throws IOException {
                onReadComplete();
                closeStreamSilently();
            }

            @Override
            public void mark(int readlimit) {
                mInputStream.mark(readlimit);
            }

            @Override
            public boolean markSupported() {
                return mInputStream.markSupported();
            }

            @Override
            public int read(@NonNull byte[] buffer) throws IOException {
                try {
                    return read(buffer, 0, buffer.length);
                }  catch (IOException e) {
                    updateException(e);
                    throw e;
                }
            }

            @Override
            public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
                try {
                    int ret = mInputStream.read(buffer, byteOffset, byteCount);
                    if (ret == -1) {
                        onReadComplete();
                    } else {
                        mBytesRead += ret;
                        onReadStart();
                    }
                    return ret;
                }  catch (IOException e) {
                    updateException(e);
                    throw e;
                }
            }

            @Override
            public synchronized void reset() throws IOException {
                try {
                    mInputStream.reset();
                }  catch (IOException e) {
                    updateException(e);
                    throw e;
                }
            }

            @Override
            public long skip(long byteCount) throws IOException {
                try {
                    return mInputStream.skip(byteCount);
                }  catch (IOException e) {
                    updateException(e);
                    throw e;
                }
            }

            private void closeStreamSilently() {
                if (mInputStream != null) {
                    try {
                        mInputStream.close();
                    } catch (Exception ignored) {}
                }
            }

            private void onReadStart() {
                if (!mReadStart) {
                    mReadStart = true;
                    mTTFB = getCurrentUTCTimeInMillis();
                }
            }

            private void updateException (Exception e) {
                mException = e;
            }
        }
    }

    private Response getResponse(final Request request) throws IOException {
        HttpURLConnection connection = openConnection(request);
        prepareRequest(connection, request);
        return readResponse(connection, request);
    }

    protected HttpURLConnection openConnection(Request request) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(request.url().toString()).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        return connection;
    }

    void prepareRequest(HttpURLConnection connection, Request request) throws IOException {
        connection.setRequestMethod(request.method());
        connection.setDoInput(true);

        Headers requestHeaders = request.headers();
        for (String key : requestHeaders.names()) {
            connection.setRequestProperty(key, requestHeaders.get(key));
        }

        RequestBody body = request.body();
        if (body != null) {
            connection.setDoOutput(true);
            if( body.contentType() != null ) {
                connection.setRequestProperty("Content-Type", body.contentType().toString());
            }
            long length = body.contentLength();
            if (length != -1) {
                connection.setFixedLengthStreamingMode((int) length);
                connection.setRequestProperty("Content-Length", String.valueOf(length));
            } else {
                connection.setChunkedStreamingMode(CHUNK_SIZE);
            }
            Sink sink = Okio.sink(connection.getOutputStream());
            BufferedSink bufferedSink = Okio.buffer(sink);
            body.writeTo(bufferedSink);
            bufferedSink.flush();
        }
    }

    Response readResponse(HttpURLConnection connection, Request request) throws IOException {
        int status = connection.getResponseCode();
        if (status < 0) {
            status = 0;
        }
        String reason = connection.getResponseMessage();
        if (reason == null) reason = ""; // HttpURLConnection treats empty reason as null.

        Headers.Builder responseHeaderBuilder = new Headers.Builder();
        if (status > 0) {
            Map<String, List<String>> headerFields = connection.getHeaderFields();
            if (headerFields != null && !headerFields.isEmpty()) {
                for (Map.Entry<String, List<String>> field : headerFields.entrySet()) {
                    String name = field.getKey();
                    if (!TextUtils.isEmpty(name)) {
                        for (String value : field.getValue()) {
                            if (!TextUtils.isEmpty(value)) {
                                responseHeaderBuilder.add(name, value);
                            }
                        }
                    }
                }
            }
        }

        String mimeType = connection.getContentType();
        int length = connection.getContentLength();
        InputStream stream;
        if (status >= 400) {
            stream = connection.getErrorStream();
        } else {
            stream = connection.getInputStream();
        }
        HttpResponseBody responseBody = new HttpResponseBody(mimeType, length, stream);
        Response response = new Response.Builder()
                .request(request)
                .headers(responseHeaderBuilder.build())
                .code(status)
                .message(reason)
                .protocol(Protocol.HTTP_1_1)
                .body(responseBody)
                .build();

        return response;
    }

    private static class HttpResponseBody extends ResponseBody {

        private final String mimeType;
        private final long length;
        private final InputStream stream;

        private HttpResponseBody(String mimeType, long length, InputStream stream) {
            this.mimeType = (mimeType != null) ? mimeType : "";
            this.length = length;
            this.stream = stream;
        }

        @Override
        public MediaType contentType() {
            return MediaType.parse(mimeType);
        }

        @Override
        public long contentLength() {
            return length;
        }

        @Override
        public BufferedSource source() {
            if (stream == null) {
                return Okio.buffer(Okio.source(new InputStream() {
                    @Override
                    public int read() throws IOException {
                        return -1;
                    }
                }));
            }
            return Okio.buffer(Okio.source(stream));
        }
    }
}
