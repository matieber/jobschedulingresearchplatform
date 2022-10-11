package edu.benchmarkandroid.service;
import android.content.Context;
import java.io.IOException;
import edu.benchmarkandroid.Benchmark.benchmarks.dogsBenchmark.Classifier;
import edu.benchmarkandroid.Benchmark.benchmarks.dogsBenchmark.MobileNetDetectionAPIModel;
import edu.benchmarkandroid.Benchmark.benchmarks.dogsBenchmark.YoloV4DetectionAPIModel;
import edu.benchmarkandroid.Benchmark.jsonConfig.ParamsRunStage;

public class TFLiteModelFactory {

    public static final String MODELS_HOME = "/Download/tf_models/";
    public static final String QUANTIZED_INPUT_8 = "quantized_input_8";
    public static final String QUANTIZED_INPUT_32 = "quantized_input_32";
    public static final String NON_QUANTIZED = "non_quantized";
    private static TFLiteModelFactory factoryInstance = null;
    private Context context;

    private TFLiteModelFactory(Context c){
        this.context = c;
    }

    public static TFLiteModelFactory getInstance(Context context){
        if (factoryInstance == null)
            factoryInstance = new TFLiteModelFactory(context);
        return factoryInstance;
    }

    public Classifier createClassifier(ParamsRunStage prs, Context context){

        String model = prs.getValue("tf_model");
        Classifier detector = null;
        try {
            if (model.contains("yolov4"))
                detector = YoloV4DetectionAPIModel.create(prs, context);
            if (model.equals("mobile-net")) {
                detector = MobileNetDetectionAPIModel.create(prs, context.getAssets());
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return detector;
    }
}
