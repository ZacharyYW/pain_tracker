import firebase_admin
from firebase_admin import credentials, firestore
import numpy as np
import xgboost as xgb
import json
import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Initialize Firebase Admin SDK
# Download a service account key from Firebase Console →
# Project Settings → Service Accounts → Generate new private key
cred = credentials.Certificate(os.path.join(BASE_DIR, 'service_account.json'))
firebase_admin.initialize_app(cred)
db = firestore.client()

def fetch_corrections(user_id: str) -> list[dict]:
    """Pull all user corrections from Firestore."""
    docs = db.collection('users').document(user_id) \
              .collection('corrections').stream()
    corrections = [doc.to_dict() for doc in docs]
    print(f"Found {len(corrections)} corrections for user {user_id}")
    return corrections

def fetch_session_windows(user_id: str, session_id: str) -> list[dict]:
    """Pull the raw window predictions stored with the original session."""
    doc = db.collection('users').document(user_id) \
            .collection('sessions').document(session_id).get()
    if not doc.exists:
        return []
    return doc.to_dict().get('windows', [])

def corrections_to_training_data(corrections: list[dict], user_id: str):
    """
    Converts corrections into (X, y) pairs the model can train on.
    Since we store scaler params, we need the already-scaled feature vectors.
    In practice you'd store the raw feature vector with each window —
    for now we use the correctedClass as the new label and re-use the
    window's feature vector from the session document.
    """
    # Load the scaler params to reconstruct normalization if needed
    with open(os.path.join(BASE_DIR, 'scaler_params.json')) as f:
        sp = json.load(f)
    scaler_mean  = np.array(sp['mean'])
    scaler_scale = np.array(sp['scale'])

    X_list, y_list = [], []

    for correction in corrections:
        session_id     = str(correction['sessionId'])
        corrected_class = int(correction['correctedClass'])
        windows = fetch_session_windows(user_id, session_id)

        if not windows:
            print(f"  No windows found for session {session_id}, skipping")
            continue

        # Each window stored in Firestore has windowIndex, timestamp, predicted, actual
        # We need the feature vector — add feature storage to the app to enable this
        # For now, we use the correction as a session-level label on all windows
        print(f"  Session {session_id}: {len(windows)} windows, "
              f"original={correction['originalPredicted']} → corrected={corrected_class}")

        # Placeholder: in a full implementation, store the scaled feature vector
        # alongside each window in Firestore, then load it here
        # X_list.append(scaled_features)
        # y_list.append(corrected_class)

    return np.array(X_list), np.array(y_list)

def retrain(user_id: str):
    print(f"\n=== Retraining for user {user_id} ===")

    corrections = fetch_corrections(user_id)
    if not corrections:
        print("No corrections to train on.")
        return

    X, y = corrections_to_training_data(corrections, user_id)

    if len(X) == 0:
        print("No feature vectors available yet. "
              "Make sure the app stores window features in Firestore.")
        return

    # Load the base model
    base_model = xgb.XGBClassifier()
    base_model.load_model(os.path.join(BASE_DIR, 'base_model.json'))

    # Fine-tune using the same warm-start approach from train1.ipynb
    personalized = xgb.XGBClassifier(
        n_estimators=20,
        max_depth=3,
        learning_rate=0.05,
        eval_metric='mlogloss',
        verbosity=0,
        n_jobs=1
    )
    personalized.fit(
        X, y,
        xgb_model=base_model.get_booster()
    )

    output_path = os.path.join(BASE_DIR, f'personalized_{user_id}.json')
    personalized.save_model(output_path)
    print(f"Saved personalized model to {output_path}")
    print("Upload this file to the device's assets/ or Firebase Storage "
          "and load it in PainPredictionModel.")

if __name__ == '__main__':
    # Replace with the actual uid from Firebase Console → Authentication
    TARGET_USER = 'PASTE_UID_HERE'
    retrain(TARGET_USER)