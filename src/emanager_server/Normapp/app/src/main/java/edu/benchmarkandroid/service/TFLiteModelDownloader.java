package edu.benchmarkandroid.service;

import android.os.AsyncTask;
import android.util.Log;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import edu.benchmarkandroid.connection.RemoteModelClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TFLiteModelDownloader {

    private String TAG = "TFLiteModelDownloader";
    private RemoteModelClient model_connectionClient;
    private Retrofit.Builder model_builder;
    private static Retrofit model_retrofit;

    public static <S> S createModelServiceClient(Class<S> serviceClass) {
        return model_retrofit.create(serviceClass);
    }

    public TFLiteModelDownloader(String localServerURL){
        model_builder =
                new Retrofit.Builder()
                        .baseUrl(localServerURL)
                        .addConverterFactory(GsonConverterFactory.create(new GsonBuilder()
                                .setLenient()
                                .create()));
        model_retrofit = model_builder.build();
        model_connectionClient = TFLiteModelDownloader.createModelServiceClient(RemoteModelClient.class);
    }

    //https://futurestud.io/tutorials/retrofit-2-how-to-download-files-from-server
    public void getModelFile(String modelURL) throws IOException {
        Call<ResponseBody> call = model_connectionClient.getModel(modelURL);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "server contacted and has the model: "+ modelURL.toString());
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            boolean writtenToDisk = writeResponseBodyToDisk(response.body());

                            Log.d(TAG, "file download was a success? " + writtenToDisk);
                            return null;
                        }
                    }.execute();

                    //Log.d(TAG, "file download was a success? " + writtenToDisk);
                } else {
                    Log.d(TAG, "server contact failed");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "error");
            }
        });

    }

    private boolean writeResponseBodyToDisk(ResponseBody body) {
            try {
                // todo change the file location/name according to your needs
                File modelFile = new File("models" + File.separator + body.string());

                InputStream inputStream = null;
                OutputStream outputStream = null;

                try {
                    byte[] fileReader = new byte[4096];

                    long fileSize = body.contentLength();
                    long fileSizeDownloaded = 0;

                    inputStream = body.byteStream();
                    outputStream = new FileOutputStream(modelFile);

                    while (true) {
                        int read = inputStream.read(fileReader);

                        if (read == -1) {
                            break;
                        }

                        outputStream.write(fileReader, 0, read);

                        fileSizeDownloaded += read;

                        Log.d(TAG, "file download: " + fileSizeDownloaded + " of " + fileSize);
                    }

                    outputStream.flush();

                    return true;
                } catch (IOException e) {
                    return false;
                } finally {
                    if (inputStream != null) {
                        inputStream.close();
                    }

                    if (outputStream != null) {
                        outputStream.close();
                    }
                }
            } catch (IOException e) {
                return false;
            }

    }
}
