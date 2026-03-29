from firebase_functions import firestore_fn, options
from firebase_admin import initialize_app, firestore, storage
import os
import tempfile

initialize_app()

MIN_CORRECTIONS = 3          # don't retrain until we have enough signal
WINDOW_MATCH_TOLERANCE_MS = 30_000   # 30s — window must be within this of correction timestamp

@firestore_fn.on_document_created(
    document="users/{userId}/corrections/{correctionId}",
    memory=options.MemoryOption.GB_1,
    timeout_sec=120
)
def auto_retrain(event: firestore_fn.Event[firestore_fn.DocumentSnapshot]) -> None:
    import xgboost as xgb
    import numpy as np

    user_id = event.params["userId"]
    db = firestore.client()
    bucket = storage.bucket()

    # ── 1. Fetch all corrections ───────────────────────────────────────────

    correction_docs = list(
        db.collection("users").document(user_id).collection("corrections").stream()
    )

    if len(correction_docs) < MIN_CORRECTIONS:
        print(f"Only {len(correction_docs)} correction(s) for {user_id} — skipping retrain.")
        return

    print(f"Found {len(correction_docs)} corrections for {user_id}, proceeding.")

    # ── 2. Build training data ─────────────────────────────────────────────

    X_list, y_list = [], []

    for doc in correction_docs:
        c = doc.to_dict()
        session_id  = str(c["sessionId"])
        corrected_class = int(c["correctedClass"])
        correction_ts   = c.get("timestamp")  # milliseconds epoch

        session_doc = (
            db.collection("users")
            .document(user_id)
            .collection("sessions")
            .document(session_id)
            .get()
        )

        if not session_doc.exists:
            print(f"  Session {session_id} not found, skipping.")
            continue

        for window in session_doc.to_dict().get("windows", []):
            if "features" not in window:
                continue

            # Only label windows temporally close to the correction timestamp.
            # If no timestamp is available, fall back to labeling all windows
            # (preserves backward compatibility with older correction records).
            if correction_ts is not None:
                window_ts = window.get("timestamp")
                if window_ts is None:
                    continue
                if abs(window_ts - correction_ts) > WINDOW_MATCH_TOLERANCE_MS:
                    continue

            X_list.append(window["features"])
            y_list.append(corrected_class)

    if not X_list:
        print("No matched feature vectors found. Check that windows have 'features' and 'timestamp'.")
        return

    print(f"Training on {len(X_list)} labeled window(s).")

    # ── 3. Retrain from base model ─────────────────────────────────────────

    X, y = np.array(X_list), np.array(y_list)

    base_model = xgb.XGBClassifier()
    base_model.load_model("base_model.json")

    personalized = xgb.XGBClassifier(
        n_estimators=20,
        max_depth=3,
        learning_rate=0.05,
        eval_metric="mlogloss",
        verbosity=0,
    )
    personalized.fit(X, y, xgb_model=base_model.get_booster())

    # ── 4. Upload to Firebase Storage ──────────────────────────────────────

    tmp_path = None
    try:
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tmp:
            tmp_path = tmp.name

        personalized.save_model(tmp_path)

        blob = bucket.blob(f"models/{user_id}/personalized_model.json")
        blob.upload_from_filename(tmp_path)
        print(f"SUCCESS: Uploaded personalized model for {user_id}.")

    except Exception as e:
        print(f"FAILED to save/upload model: {e}")
        raise  # re-raise so Cloud Functions marks the invocation as failed

    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.remove(tmp_path)