package com.example.pain_tracker.model

import android.content.Context
import biz.k11i.xgboost.Predictor
import biz.k11i.xgboost.util.FVec
import com.google.gson.Gson
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.text.get

class PainPredictionModel(context: Context) {

    private lateinit var predictor: Predictor
    private lateinit var scalerMean: FloatArray
    private lateinit var scalerScale: FloatArray

    init {
        // Load Model and Scaler (Same as before)
        val modelStream = context.assets.open("base_model.json")
        predictor = Predictor(modelStream)

        val scalerStream = context.assets.open("scaler_params.json")
        val gson = Gson()
        val scalerData = gson.fromJson(InputStreamReader(scalerStream), ScalerParams::class.java)
        
        scalerMean = scalerData.mean.toFloatArray()
        scalerScale = scalerData.scale.toFloatArray()
    }

    // --- NEW: Preprocessing logic mirroring preprocess.py ---
    fun extractFeaturesAndPredict(window: List<RawSensorRow>): Int {
        if (window.isEmpty()) return -1 // Invalid input
        
        // 1. Extract raw arrays
        val hrArray = window.map { it.hr }
        val ibiArray = window.flatMap { it.ibi } // Flattens all IBI lists in the window into one array
        
        // 2. Calculate HR Features
        val hrMean = hrArray.average().toFloat()
        val hrStd = calculatePopulationStd(hrArray).toFloat() // NumPy uses population std dev by default
        val hrMin = hrArray.minOrNull() ?: 0f
        val hrMax = hrArray.maxOrNull() ?: 0f
        val hrRange = hrMax - hrMin

        // 3. Calculate IBI Features
        var ibiMean = 0f; var ibiStd = 0f; var ibiMin = 0f; var ibiMax = 0f; var ibiRange = 0f
        var ibiFirstDiffMean = 0f; var ibiSecondDiffMean = 0f; var rmssd = 0f; var pnn50 = 0f

        if (ibiArray.size > 1) {
            val ibiFloats = ibiArray.map { it.toFloat() }
            ibiMean = ibiFloats.average().toFloat()
            ibiStd = calculatePopulationStd(ibiFloats).toFloat()
            ibiMin = ibiFloats.minOrNull() ?: 0f
            ibiMax = ibiFloats.maxOrNull() ?: 0f
            ibiRange = ibiMax - ibiMin
            
            // Calculate differences
            val firstDiffs = ibiFloats.zipWithNext { a, b -> abs(b - a) }
            ibiFirstDiffMean = firstDiffs.average().toFloat()
            
            if (firstDiffs.size > 1) {
                val secondDiffs = firstDiffs.zipWithNext { a, b -> abs(b - a) }
                ibiSecondDiffMean = secondDiffs.average().toFloat()
            }
            
            // RMSSD (Root Mean Square of Successive Differences)
            val sqDiffs = firstDiffs.map { it.pow(2) }
            rmssd = sqrt(sqDiffs.average().toFloat())
            
            // pNN50 (proportion of differences > 50ms)
            val countOver50 = firstDiffs.count { it > 50f }
            pnn50 = countOver50.toFloat() / ibiArray.size.toFloat()
        }

        // 4. Calculate ECG Variance (variance of the FIRST row's ECG array, as per Python)
        val firstEcg = window.first().ecg
        val ecgVar = if (firstEcg.isNotEmpty()) calculatePopulationVariance(firstEcg).toFloat() else 0f

        // 5. Construct the 15-feature array (MUST match the exact order of columns in Python)
        val extractedFeatures = floatArrayOf(
            hrMean, hrStd, hrMin, hrMax, hrRange,
            ibiMean, ibiStd, ibiMin, ibiMax, ibiRange,
            ibiFirstDiffMean, ibiSecondDiffMean, rmssd, pnn50,
            ecgVar
        )

        // 6. Scale and Predict (Using previous logic)
        val scaledFeatures = FloatArray(extractedFeatures.size)
        for (i in extractedFeatures.indices) {
            scaledFeatures[i] = (extractedFeatures[i] - scalerMean[i]) / scalerScale[i]
        }

        val featureVector = FVec.Transformer.fromArray(scaledFeatures, true)
        val probabilities = predictor.predict(featureVector)
        
        return probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
    }

    // --- Math Helper Functions (Mirroring NumPy's default behaviors) ---
    private fun calculatePopulationVariance(data: List<Float>): Double {
        val mean = data.average()
        return data.sumOf { (it - mean).pow(2) } / data.size // Divide by N, not N-1
    }

    private fun calculatePopulationStd(data: List<Float>): Double {
        return sqrt(calculatePopulationVariance(data))
    }

    private data class ScalerParams(val mean: List<Float>, val scale: List<Float>)
}