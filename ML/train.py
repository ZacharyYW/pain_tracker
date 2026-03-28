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
    def __init__(self, X, y, augment=False):
        self.X = torch.tensor(X.values, dtype=torch.float32)
        # Ensure labels are 0-indexed. If your data is 1-4, this shifts to 0-3.
        # If your data is already 0-3, this shouldn't be needed, but it's safe.
        labels = y.values
        if labels.min() == 1:
            labels = labels - 1
        self.y = torch.tensor(labels, dtype=torch.long)
        self.augment = augment
        
    def __len__(self):
        return len(self.y)
        
    def __getitem__(self, idx):
        features = self.X[idx]
        if self.augment:
            noise = torch.randn_like(features) * 0.01
            features = features + noise
        return features, self.y[idx]

# --- 2. Define the 3-Layer MLP Base Model ---
class PainTrackerMLP(nn.Module):
    def __init__(self, input_size, num_classes=4): # Adjusted to 4 distinct levels
        super(PainTrackerMLP, self).__init__()
        
        # Layer 1: Input -> 64 nodes
        self.layer1 = nn.Linear(input_size, 64)
        self.relu1 = nn.ReLU()
        self.dropout1 = nn.Dropout(0.3) # Regularization to help accuracy
        
        # Layer 2: 64 nodes -> 32 nodes
        self.layer2 = nn.Linear(64, 32)
        self.relu2 = nn.ReLU()
        self.dropout2 = nn.Dropout(0.3)
        
        # Layer 3 (Output): 32 nodes -> 4 pain levels
        self.output_layer = nn.Linear(32, num_classes)
        
    def forward(self, x):
        x = self.dropout1(self.relu1(self.layer1(x)))
        x = self.dropout2(self.relu2(self.layer2(x)))
        return self.output_layer(x)

# --- 3. Training and Evaluation Loop ---
def run_pipeline():
    data_path = os.path.join(BASE_DIR, 'processed_data.csv')
    if not os.path.exists(data_path):
        print("Could not find processed_data.csv. Did you run preprocess.py?")
        return
        
    df = pd.read_csv(data_path)
    feature_cols = [col for col in df.columns if col not in ['pain_level', 'person_id']]
    input_size = len(feature_cols)
    people_ids = df['person_id'].unique()
    
    # Hyperparameters
    epochs = 40 
    batch_size = 32
    learning_rate = 0.001
    weight_decay = 1e-4
    
    print(f"Starting LOOCV for {len(people_ids)} people...")
    print(f"Configured for 4 Pain Levels | Features: {input_size}\n")
    
    overall_accuracies = []
    
    # The LOOCV Loop
    for test_person in people_ids:
        print(f"--- Fold: Testing on Person ID {test_person} ---")
        train_df = df[df['person_id'] != test_person]
        test_df = df[df['person_id'] == test_person]
        
        train_loader = DataLoader(PainDataset(train_df[feature_cols], train_df['pain_level'], augment=True), batch_size=batch_size, shuffle=True)
        test_loader = DataLoader(PainDataset(test_df[feature_cols], test_df['pain_level'], augment=False), batch_size=batch_size, shuffle=False)
        
        model = PainTrackerMLP(input_size=input_size)
        criterion = nn.CrossEntropyLoss() 
        optimizer = optim.Adam(model.parameters(), lr=learning_rate, weight_decay=weight_decay)
        
        model.train()
        for epoch in range(epochs):
            for batch_X, batch_y in train_loader:
                optimizer.zero_grad()
                outputs = model(batch_X)
                loss = criterion(outputs, batch_y)
                loss.backward()
                optimizer.step()
                
        model.eval()
        correct, total = 0, 0
        with torch.no_grad():
            for batch_X, batch_y in test_loader:
                outputs = model(batch_X)
                _, predicted = torch.max(outputs.data, 1)
                total += batch_y.size(0)
                correct += (predicted == batch_y).sum().item()
        
        accuracy = 100 * correct / total if total > 0 else 0
        overall_accuracies.append(accuracy)
        print(f"Accuracy for Person {test_person}: {accuracy:.2f}%\n")
        
    print("=========================================")
    print(f"Average LOOCV Accuracy (4-Class): {np.mean(overall_accuracies):.2f}%")
    print("=========================================")

    # Save Final Base Model
    print("Saving base_model.pth for mobile handoff...")
    final_model = PainTrackerMLP(input_size=input_size)
    torch.save(final_model.state_dict(), os.path.join(BASE_DIR, 'base_model.pth'))

if __name__ == "__main__":
    run_pipeline()