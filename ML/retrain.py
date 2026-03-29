import firebase_admin
from firebase_admin import credentials, firestore, storage
import numpy as np
import xgboost as xgb
import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Initialize Firebase Admin SDK
cred = credentials.Certificate(os.path.join(BASE_DIR, 'service_account.json'))

# REPLACE with your actual Firebase Storage bucket name (e.g., 'your-app-123.appspot.com')
firebase_admin.initialize_app(cred, {
    'storageBucket': 'REPLACE_ME.appspot.com'
})

db = firestore.client()
bucket = storage.bucket()

def fetch_training_data(user_id: str):
    X_list, y_list = [], []

    # 1. Fetch user corrections
    docs = db.collection('users').document(user_id).collection('corrections').stream()
    corrections = [doc.to_dict() for doc in docs]
    print(f"Found {len(corrections)} corrections for user {user_id}")

    # 2. Iterate and match with session features
    for correction in corrections:
        session_id = str(correction['sessionId'])
        corrected_class = int(correction['correctedClass'])

        session_doc = db.collection('users').document(user_id).collection('sessions').document(session_id).get()
        if not session_doc.exists:
            print(f"  Session {session_id} not found, skipping.")
            continue

        session_data = session_doc.to_dict()
        windows = session_data.get('windows', [])

        # Extract features and pair with the corrected label
        for window in windows:
            if 'features' in window:
                X_list.append(window['features'])
                y_list.append(corrected_class)

    return np.array(X_list), np.array(y_list)

def retrain_and_upload(user_id: str):
    print(f"\n=== Retraining for user {user_id} ===")

    X, y = fetch_training_data(user_id)

    if len(X) == 0:
        print("No feature vectors available yet. Make sure the app is uploading them.")
        return

    # Load base model
    base_model = xgb.XGBClassifier()
    base_model.load_model(os.path.join(BASE_DIR, 'base_model.json'))

    # Fine-tune model
    personalized = xgb.XGBClassifier(
        n_estimators=20,
        max_depth=3,
        learning_rate=0.05,
        eval_metric='mlogloss',
        verbosity=0
    )
    personalized.fit(X, y, xgb_model=base_model.get_booster())

    # Save locally
    local_path = os.path.join(BASE_DIR, f'personalized_{user_id}.json')
    personalized.save_model(local_path)
    print(f"Saved local model to {local_path}")

    # Upload to Firebase Storage
    try:
        blob = bucket.blob(f'models/{user_id}/personalized_model.json')
        blob.upload_from_filename(local_path)
        print(f"SUCCESS: Uploaded personalized model to Firebase Storage!")
    except Exception as e:
        print(f"FAILED to upload to Firebase Storage. Did you set the storageBucket? Error: {e}")

if __name__ == '__main__':
    # Replace with a real Firebase UID to test
    TARGET_USER = 'PASTE_UID_HERE'
    retrain_and_upload(TARGET_USER)