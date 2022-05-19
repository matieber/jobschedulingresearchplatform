package edu.benchmarkandroid.connection;


import com.google.gson.JsonObject;
import edu.benchmarkandroid.Benchmark.jsonConfig.BenchmarksResponse;
import edu.benchmarkandroid.model.UpdateData;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ConnectionClient {

    @GET("{model}")
    Call<BenchmarksResponse> getBenchmarks(@Path("model") String model);


    @GET("{model}")
    Call<JsonObject> startBenchmark(@Path("model") String model, @Query("stage") String stage,  // Call with stage = "postinit"
                                    @Query("requiredBatteryState") String stateOfCharge);

    @Headers({"Content-Type: application/x-www-form-urlencoded; charset=UTF-8"})
    @PUT("{model}")
    Call<JsonObject> putUpdateBatteryState(@Path("model") String model, @Body UpdateData updateData);


    @Multipart
    @POST("{model}")
    Call<JsonObject> postResult(@Path("model") String model, @Query("fileName") String filename, @Part MultipartBody.Part body);


    @DELETE("{model}")
    Call<JsonObject> deleteDevice(@Path("model") String model, @Query("ip") String ipAddr);

    @Headers({"Content-Type: application/x-www-form-urlencoded; charset=UTF-8"})
    @PUT("{model}")
    Call<JsonObject> switchState(@Path("model") String model, @Query("requiredEnergyState") String requiredEnergyState, @Query("slotId") String slotId);

}
