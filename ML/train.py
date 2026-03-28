import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import pandas as pd
import numpy as np
import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# --- 1. Define the PyTorch Dataset ---
class PainDataset(Dataset):
    def __init__(self, X, y):
        # PyTorch requires specific tensor data types for features (float32) and labels (long)
        self.X = torch.tensor(X.values, dtype=torch.float32)
        self.y = torch.tensor(y.values, dtype=torch.long)
        
    def __len__(self):
        return len(self.y)
        
    def __getitem__(self, idx):
        return self.X[idx], self.y[idx]

# --- 2. Define the 3-Layer MLP Base Model ---
class PainTrackerMLP(nn.Module):
    def __init__(self, input_size, num_classes=5): # 5 classes for pain levels 0-4
        super(PainTrackerMLP, self).__init__()
        
        # Layer 1: Input -> 64 nodes
        self.layer1 = nn.Linear(input_size, 64)
        self.relu1 = nn.ReLU()
        
        # Layer 2: 64 nodes -> 32 nodes
        self.layer2 = nn.Linear(64, 32)
        self.relu2 = nn.ReLU()
        
        # Layer 3 (Output): 32 nodes -> 5 pain levels
        self.output_layer = nn.Linear(32, num_classes)
        
    def forward(self, x):
        x = self.relu1(self.layer1(x))
        x = self.relu2(self.layer2(x))
        return self.output_layer(x)

# --- 3. Training and Evaluation Loop ---
def run_loocv():
    data_path = os.path.join(BASE_DIR, 'processed_data.csv')
    if not os.path.exists(data_path):
        print("Could not find processed_data.csv. Did you run preprocess.py?")
        return
        
    df = pd.read_csv(data_path)
    
    # Grab our feature columns dynamically
    feature_cols = [col for col in df.columns if col not in ['pain_level', 'person_id']]
    input_size = len(feature_cols)
    
    people_ids = df['person_id'].unique()
    print(f"Starting Leave-One-Out CV for {len(people_ids)} people...")
    print(f"Input Features: {input_size} | Model: 3-Layer MLP\n")
    
    # Hyperparameters
    epochs = 30
    batch_size = 32
    learning_rate = 0.001
    
    overall_accuracies = []
    
    # The LOOCV Loop
    for test_person in people_ids:
        print(f"--- Fold: Holding out Person ID {test_person} for testing ---")
        
        # Split data based on person_id
        train_df = df[df['person_id'] != test_person]
        test_df = df[df['person_id'] == test_person]
        
        # Create DataLoaders
        train_dataset = PainDataset(train_df[feature_cols], train_df['pain_level'])
        test_dataset = PainDataset(test_df[feature_cols], test_df['pain_level'])
        
        train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True)
        test_loader = DataLoader(test_dataset, batch_size=batch_size, shuffle=False)
        
        # Initialize a fresh model for each fold so data doesn't leak
        model = PainTrackerMLP(input_size=input_size)
        criterion = nn.CrossEntropyLoss() # Standard loss for multi-class classification
        optimizer = optim.Adam(model.parameters(), lr=learning_rate)
        
        # --- Train ---
        model.train()
        for epoch in range(epochs):
            for batch_X, batch_y in train_loader:
                optimizer.zero_grad()
                outputs = model(batch_X)
                loss = criterion(outputs, batch_y)
                loss.backward()
                optimizer.step()
                
        # --- Evaluate ---
        model.eval()
        correct = 0
        total = 0
        with torch.no_grad():
            for batch_X, batch_y in test_loader:
                outputs = model(batch_X)
                _, predicted = torch.max(outputs.data, 1)
                total += batch_y.size(0)
                correct += (predicted == batch_y).sum().item()
        
        accuracy = 100 * correct / total if total > 0 else 0
        overall_accuracies.append(accuracy)
        print(f"Accuracy on unseen data (Person {test_person}): {accuracy:.2f}%\n")
        
    print("=========================================")
    print(f"Average LOOCV Accuracy: {np.mean(overall_accuracies):.2f}%")
    print("=========================================")

if __name__ == "__main__":
    run_loocv()