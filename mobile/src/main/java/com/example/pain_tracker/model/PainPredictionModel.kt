package com.example.pain_tracker.model

import android.content.Context
import biz.k11i.xgboost.Predictor
import biz.k11i.xgboost.util.FVec
import com.google.gson.Gson
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.sqrt

class PainPredictionModel(context: Context) {

    companion object {
        // The XGBoost model was trained on sliding windows of exactly 10 rows.
        // Passing any other size will produce incorrect feature values.
        const val WINDOW_SIZE = 10
    }

    private val predictor: Predictor
    // BUG FIX #2: store scaler params as Double to preserve the full
    // JSON precision (values like 71.24631646380526 are truncated when
    // loaded into Float, which shifts the normalized feature values).
    private val scalerMean: DoubleArray
    private val scalerScale: DoubleArray

    init {
        val modelStream = context.assets.open("base_model.json")
        predictor = Predictor(modelStream)

        val scalerStream = context.assets.open("scaler_params.json")
        // BUG FIX #2: deserialize into List<Double> instead of List<Float>
        val scalerData = Gson().fromJson(InputStreamReader(scalerStream), ScalerParams::class.java)
        scalerMean  = scalerData.mean.toDoubleArray()
        scalerScale = scalerData.scale.toDoubleArray()
    }

    fun extractFeaturesAndPredict(window: List<RawSensorRow>): Int {
        // BUG FIX #3: reject windows that are not exactly WINDOW_SIZE rows.
        // The model was trained on 10-row windows; any other size produces
        // wrong IBI pool sizes and wrong HR statistics.
        if (window.size != WINDOW_SIZE) return -1

        // --- HR features (all rows) ---
        val hrArray = window.map { it.hr }
        val hrMean  = hrArray.average().toFloat()
        val hrStd   = populationStd(hrArray).toFloat()
        val hrMin   = hrArray.minOrNull() ?: 0f
        val hrMax   = hrArray.maxOrNull() ?: 0f
        val hrRange = hrMax - hrMin

        // --- IBI features (all IBI values flattened across the window) ---
        // Mirrors Python: all_ibis = [v for sublist in window['ibi'] for v in sublist]
        val ibiInts = window.flatMap { it.ibi }

        var ibiMean           = 0f
        var ibiStd            = 0f
        var ibiMin            = 0f
        var ibiMax            = 0f
        var ibiRange          = 0f
        var ibiFirstDiffMean  = 0f
        var ibiSecondDiffMean = 0f
        var rmssd             = 0f
        var pnn50             = 0f

        // Python uses ibis_arr = [0] (zeroing all features) when len <= 1,
        // which is equivalent to leaving the defaults at 0.
        if (ibiInts.size > 1) {
            val ibi = ibiInts.map { it.toFloat() }

            ibiMean  = ibi.average().toFloat()
            ibiStd   = populationStd(ibi).toFloat()
            ibiMin   = ibi.minOrNull() ?: 0f
            ibiMax   = ibi.maxOrNull() ?: 0f
            ibiRange = ibiMax - ibiMin

            // first_diffs = np.abs(np.diff(ibis_arr))
            val firstDiffs = ibi.zipWithNext { a, b -> abs(b - a) }
            ibiFirstDiffMean = firstDiffs.average().toFloat()

            // second_diff_mean = mean(|diff(first_diffs)|) when len > 1
            if (firstDiffs.size > 1) {
                val secondDiffs = firstDiffs.zipWithNext { a, b -> abs(b - a) }
                ibiSecondDiffMean = secondDiffs.average().toFloat()
            }

            // RMSSD = sqrt(mean(first_diffs^2))
            // BUG FIX #1: Float.pow(Int) does not exist in kotlin.math;
            // use it * it (Float * Float = Float) instead.
            val sqDiffs = firstDiffs.map { it * it }
            rmssd = sqrt(sqDiffs.average().toFloat())

            // pNN50: proportion of |diffs| > 50 ms, denominator = total IBI count
            val countOver50 = firstDiffs.count { it > 50f }
            pnn50 = countOver50.toFloat() / ibiInts.size.toFloat()
        }

        // --- ECG feature: variance of the FIRST row's ECG array ---
        // Mirrors Python: first_ecg = window['ecg'].iloc[0]
        val firstEcg = window.first().ecg
        val ecgVar = if (firstEcg.isNotEmpty()) populationVariance(firstEcg).toFloat() else 0f

        // --- Construct the 15-feature vector (order must match training columns) ---
        val features = floatArrayOf(
            hrMean, hrStd, hrMin, hrMax, hrRange,
            ibiMean, ibiStd, ibiMin, ibiMax, ibiRange,
            ibiFirstDiffMean, ibiSecondDiffMean, rmssd, pnn50,
            ecgVar
        )

        // --- Scale: z = (x - mean) / scale, using Double precision ---
        val scaled = FloatArray(features.size) { i ->
            ((features[i] - scalerMean[i]) / scalerScale[i]).toFloat()
        }

        val fvec = FVec.Transformer.fromArray(scaled, true)
        val probs = predictor.predict(fvec)
        return probs.indices.maxByOrNull { probs[it] } ?: 0
    }

    // --- Math helpers mirroring NumPy defaults (population, ddof=0) ---

    private fun populationVariance(data: List<Float>): Double {
        val mean = data.average()
        return data.sumOf { (it - mean) * (it - mean) } / data.size
    }

    private fun populationStd(data: List<Float>): Double = sqrt(populationVariance(data))

    // BUG FIX #2: List<Double> matches the JSON numeric type
    private data class ScalerParams(val mean: List<Double>, val scale: List<Double>)
}