package com.example.pain_tracker.repository

import android.content.Context
import com.example.pain_tracker.model.CorrectionRecord
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query.Direction
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.example.pain_tracker.model.PainSession
import com.example.pain_tracker.model.PainZone
import com.example.pain_tracker.model.PredictionPipeline
import com.example.pain_tracker.model.SessionSource
import com.example.pain_tracker.model.Symptom
import com.example.pain_tracker.model.ZoneLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File

object FirestoreRepository {

    private val db   get() = Firebase.firestore
    private val auth get() = Firebase.auth

    val userId: String // Expose getter
        get() = auth.currentUser?.uid
            ?: error("No authenticated user. Call signInAnonymously() first.")

    private fun sessionsCollection() =
        db.collection("users").document(userId).collection("sessions")

    // ── Auth ──────────────────────────────────────────────────────────────

    suspend fun signInAnonymously() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    // ── Download ML Model ──────────────────────────────────────────────────

    // --- NEW: Model downloader ---
    fun downloadPersonalizedModel(context: Context) {
        try {
            val uid = userId
            val storageRef = FirebaseStorage.getInstance().reference
            val modelRef = storageRef.child("models/$uid/personalized_model.json")

            val localFile = File(context.filesDir, "personalized_model.json")

            modelRef.getFile(localFile)
                .addOnSuccessListener {
                    println("SUCCESS: Personalized model downloaded!")
                }
                .addOnFailureListener {
                    println("No personalized model found, using base model.")
                }
        } catch (e: Exception) {
            println("Failed to initiate model download: ${e.message}")
        }
    }

    // ── Write ──────────────────────────────────────────────────────────────

    suspend fun saveWatchSession(
        session: PainSession,
        result: PredictionPipeline.SessionResult,
    ): String {
        val doc = sessionsCollection().document(session.id.toString())
        doc.set(sessionToMap(session) + watchResultExtras(result)).await()
        return doc.id
    }

    suspend fun saveManualSession(session: PainSession): String {
        val doc = sessionsCollection().document(session.id.toString())
        doc.set(sessionToMap(session)).await()
        return doc.id
    }

    suspend fun updateSession(id: Long, fields: Map<String, Any>) {
        sessionsCollection().document(id.toString()).update(fields).await()
    }

    suspend fun deleteSession(id: Long) {
        sessionsCollection().document(id.toString()).delete().await()
    }

    suspend fun updateSessionFields(session: PainSession) {
        sessionsCollection().document(session.id.toString())
            .update(
                mapOf(
                    "startTime" to session.startTime,
                    "endTime"   to session.endTime,
                    "peakLevel" to session.peakLevel,
                    "symptoms"  to session.symptoms.map { it.name },
                    "notes"     to session.notes,
                    "zones"     to session.zones.map { zoneToMap(it) },
                )
            ).await()
    }

    suspend fun saveCorrection(correction: CorrectionRecord) {
        db.collection("users").document(userId)
            .collection("corrections")
            .document(correction.sessionId.toString())
            .set(
                mapOf(
                    "sessionId"          to correction.sessionId,
                    "timestamp"          to correction.timestamp,
                    "originalPredicted"  to correction.originalPredicted,
                    "correctedPainLevel" to correction.correctedPainLevel,
                    "correctedClass"     to correction.correctedClass,
                    "windowTimestamps"   to correction.windowTimestamps,
                )
            ).await()
    }

    // ── Read ───────────────────────────────────────────────────────────────

    fun sessionStream(): Flow<List<PainSession>> = callbackFlow {
        val sub = sessionsCollection()
            .orderBy("startTime", Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val sessions = snap.documents.mapNotNull {
                    docToSession(it.data ?: return@mapNotNull null)
                }
                trySend(sessions)
            }
        awaitClose { sub.remove() }
    }

    suspend fun fetchSessions(): List<PainSession> {
        val snap = sessionsCollection()
            .orderBy("startTime", Direction.DESCENDING)
            .get().await()
        return snap.documents.mapNotNull { docToSession(it.data ?: return@mapNotNull null) }
    }

    // ── Serialization ──────────────────────────────────────────────────────

    private fun sessionToMap(session: PainSession): Map<String, Any?> = mapOf(
        "startTime" to session.startTime,
        "endTime"   to session.endTime,
        "source"    to session.source.name,
        "peakLevel" to session.peakLevel,
        "symptoms"  to session.symptoms.map { it.name },
        "notes"     to session.notes,
        "zones"     to session.zones.map { zoneToMap(it) },
    )

    private fun watchResultExtras(result: PredictionPipeline.SessionResult): Map<String, Any?> = mapOf(
        "dominantPrediction" to result.dominantPrediction,
        "accuracy"           to result.accuracy,
        "windows"            to result.windows.map { w ->
            mapOf(
                "windowIndex" to w.windowIndex,
                "timestamp"   to w.timestamp,
                "predicted"   to w.predicted,
                "actual"      to w.actual,
                "features"    to w.features // --- NEW: Upload features array ---
            )
        },
    )

    private fun zoneToMap(zone: PainZone): Map<String, Any> = mapOf(
        "level"           to zone.level.name,
        "durationMinutes" to zone.durationMinutes,
    )

    private fun docToSession(data: Map<String, Any>): PainSession? = runCatching {
        @Suppress("UNCHECKED_CAST")
        PainSession(
            id        = data["startTime"] as Long,
            startTime = data["startTime"] as Long,
            endTime   = data["endTime"]   as Long,
            source    = SessionSource.valueOf(data["source"] as String),
            peakLevel = (data["peakLevel"] as Number).toFloat(),
            symptoms  = (data["symptoms"] as? List<String>)
                ?.mapNotNull { runCatching { Symptom.valueOf(it) }.getOrNull() }
                ?.toSet() ?: emptySet(),
            notes     = data["notes"] as? String ?: "",
            zones     = (data["zones"] as? List<Map<String, Any>>)
                ?.map { mapToZone(it) } ?: emptyList(),
        )
    }.getOrNull()

    private fun mapToZone(m: Map<String, Any>) = PainZone(
        level           = ZoneLevel.valueOf(m["level"] as String),
        durationMinutes = (m["durationMinutes"] as Number).toInt(),
    )
}