package com.example.pain_tracker.model

import android.content.Context
import biz.k11i.xgboost.Predictor
import biz.k11i.xgboost.util.FVec
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.sqrt

class PainPredictionModel(context: Context) {

    companion object {
        const val WINDOW_SIZE = 10
    }

    private val predictor: Predictor
    private val scalerMean: DoubleArray
    private val scalerScale: DoubleArray

    init {
        // --- NEW: Check for downloaded personalized model ---
        val personalizedFile = File(context.filesDir, "personalized_model.json")

        val modelStream = if (personalizedFile.exists()) {
            println("Loading PERSONALIZED model from internal storage")
            FileInputStream(personalizedFile)
        } else {
            println("Loading BASE model from assets")
            context.assets.open("base_model.bin")
        }

        predictor = Predictor(modelStream)

        // Keep scaler logic the same
        val scalerStream = context.assets.open("scaler_params.json")
        val scalerData = Gson().fromJson(InputStreamReader(scalerStream), ScalerParams::class.java)
        scalerMean  = scalerData.mean.toDoubleArray()
        scalerScale = scalerData.scale.toDoubleArray()
    }

    fun extractFeaturesAndPredict(window: List<RawSensorRow>): Pair<Int, FloatArray>? {
        if (window.size != WINDOW_SIZE) return null

        val hrArray = window.map { it.hr }
        val hrMean  = hrArray.average().toFloat()
        val hrStd   = populationStd(hrArray).toFloat()
        val hrMin   = hrArray.minOrNull() ?: 0f
        val hrMax   = hrArray.maxOrNull() ?: 0f
        val hrRange = hrMax - hrMin

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

        if (ibiInts.size > 1) {
            val ibi = ibiInts.map { it.toFloat() }

            ibiMean  = ibi.average().toFloat()
            ibiStd   = populationStd(ibi).toFloat()
            ibiMin   = ibi.minOrNull() ?: 0f
            ibiMax   = ibi.maxOrNull() ?: 0f
            ibiRange = ibiMax - ibiMin

            val firstDiffs = ibi.zipWithNext { a, b -> abs(b - a) }
            ibiFirstDiffMean = firstDiffs.average().toFloat()

            if (firstDiffs.size > 1) {
                val secondDiffs = firstDiffs.zipWithNext { a, b -> abs(b - a) }
                ibiSecondDiffMean = secondDiffs.average().toFloat()
            }

            val sqDiffs = firstDiffs.map { it * it }
            rmssd = sqrt(sqDiffs.average().toFloat())

            val countOver50 = firstDiffs.count { it > 50f }
            pnn50 = countOver50.toFloat() / ibiInts.size.toFloat()
        }

        val firstEcg = window.first().ecg
        val ecgVar = if (firstEcg.isNotEmpty()) populationVariance(firstEcg).toFloat() else 0f

        val features = floatArrayOf(
            hrMean, hrStd, hrMin, hrMax, hrRange,
            ibiMean, ibiStd, ibiMin, ibiMax, ibiRange,
            ibiFirstDiffMean, ibiSecondDiffMean, rmssd, pnn50,
            ecgVar
        )

        val scaled = FloatArray(features.size) { i ->
            ((features[i] - scalerMean[i]) / scalerScale[i]).toFloat()
        }

        val fvec = FVec.Transformer.fromArray(scaled, true)
        val probs = predictor.predict(fvec)
        val prediction = probs.indices.maxByOrNull { probs[it] } ?: 0

        // --- NEW: Return Both ---
        return Pair(prediction, scaled)
    }

    private fun populationVariance(data: List<Float>): Double {
        val mean = data.average()
        return data.sumOf { (it - mean) * (it - mean) } / data.size
    }

    private fun populationStd(data: List<Float>): Double = sqrt(populationVariance(data))

    private data class ScalerParams(val mean: List<Double>, val scale: List<Double>)
}