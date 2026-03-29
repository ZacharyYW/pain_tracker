import os
import pandas as pd
import numpy as np
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import accuracy_score, confusion_matrix
from sklearn.model_selection import LeaveOneGroupOut
from sklearn.utils.class_weight import compute_sample_weight
from xgboost import XGBClassifier
import matplotlib.pyplot as plt
import seaborn as sns

print("1. Imports successful.")

# Define BASE_DIR and load data
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
data_path = os.path.join(BASE_DIR, 'processed_data.csv')

print(f"2. Loading data from {data_path}...")
df = pd.read_csv(data_path)

# Setup variables
feature_cols = [col for col in df.columns if col not in ['pain_level', 'person_id']]
X = df[feature_cols].values
y = df['pain_level'].values.astype(int)
groups = df['person_id'].values

logo = LeaveOneGroupOut()

# Define helper function
def plot_confusion_matrix(true, pred, title, num_classes=4):
    cm = confusion_matrix(true, pred)
    plt.figure(figsize=(6,5))
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues',
                xticklabels=[f'Level {i}' for i in range(num_classes)],
                yticklabels=[f'Level {i}' for i in range(num_classes)])
    plt.title(title)
    plt.ylabel("True")
    plt.xlabel("Predicted")
    plt.tight_layout()
    plt.show()

print("3. Starting XGBoost Cross-Validation...")

xgb_scores = []
xgb_true = []
xgb_pred = []

for fold_num, (train_idx, test_idx) in enumerate(logo.split(X, y, groups)):
    print(f"\n--- Processing Fold {fold_num + 1} ---")
    
    X_train, X_test = X[train_idx], X[test_idx]
    y_train, y_test = y[train_idx], y[test_idx]

    scaler = StandardScaler()
    X_train = scaler.fit_transform(X_train)
    X_test = scaler.transform(X_test)
    
    model = XGBClassifier(
        n_estimators=100,
        max_depth=3,
        learning_rate=0.1,
        subsample=0.8,
        eval_metric='mlogloss',
        verbosity=0,
        n_jobs=1  # Keeping this to prevent OpenMP conflicts
    )
    
    weights = compute_sample_weight('balanced', y_train)
    
    print("Fitting model (If it crashes, it will likely happen right after this line)...")
    model.fit(X_train, y_train, sample_weight=weights)
    
    print("Predicting...")
    preds = model.predict(X_test)

    acc = accuracy_score(y_test, preds) * 100
    xgb_scores.append(acc)
    xgb_true.extend(y_test.tolist())
    xgb_pred.extend(preds.tolist())
    print(f"Fold {fold_num + 1} accuracy: {acc:.2f}%")

print(f"\n=== Final XGBoost LOOCV Accuracy: {np.mean(xgb_scores):.2f}% ===")
plot_confusion_matrix(xgb_true, xgb_pred, "XGBoost Confusion Matrix")