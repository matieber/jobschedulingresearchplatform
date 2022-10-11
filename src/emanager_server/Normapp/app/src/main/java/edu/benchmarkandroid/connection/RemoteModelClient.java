package edu.benchmarkandroid.connection;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public interface RemoteModelClient {

    @Streaming
    @GET
    Call<ResponseBody> getModel(@Url String modelUrl);
}
