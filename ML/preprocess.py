
from sklearn.preprocessing import StandardScaler
import joblib
import pandas as pd
import numpy as np
import os

# Get the absolute path of the directory where preprocess.py lives
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Construct the path: go up one level from ML, then into pain_tracker_data/files
DATA_DIR = os.path.join(BASE_DIR, '..', 'pain_tracker_data', 'files')

def load_and_clean_data():
    # --- Step 1: Load and combine CSVs ---
    dataframes = []
    person_id_map = {}  # Dictionary to track { 'name': person_id }
    person_id_counter = 0
    
    # Safety check to make sure the directory actually exists
    if not os.path.exists(DATA_DIR):
        print(f"Error: Could not find the directory at {DATA_DIR}")
        return None

    # Loop through the directory and grab matching CSVs
    for filename in os.listdir(DATA_DIR):
        # Enforce the naming convention
        if filename.endswith(".csv") and "_pain_tracker_" in filename:
            # Extract the name (everything before '_pain_tracker_')
            name = filename.split("_pain_tracker_")[0]
            
            # If we haven't seen this person before, give them a new ID
            if name not in person_id_map:
                person_id_map[name] = person_id_counter
                person_id_counter += 1
            
            # Grab the correct ID for this person
            current_person_id = person_id_map[name]
            
            filepath = os.path.join(DATA_DIR, filename)
            df = pd.read_csv(filepath)
            
            # Add the consistent person_id column
            df['person_id'] = current_person_id
            dataframes.append(df)
            print(f"Loaded {filename} for person '{name}' (ID: {current_person_id})")
        elif filename.endswith(".csv"):
            print(f"Ignored {filename} (did not match naming convention)")

    # Combine everything and reset the index
    if not dataframes:
        print("No matching CSVs found! Check your folder and filenames.")
        return None
        
    full_df = pd.concat(dataframes, ignore_index=True)
    print(f"\nOriginal combined row count: {len(full_df)}")

    # --- Step 2: Filter rows ---
    # Drop rows where pain_level == -1 (unlabeled baseline)
    filtered_df = full_df[full_df['pain_level'] != -1].copy()
    
    # Drop rows where hr == 0 (ECG interference artifact)
    filtered_df = filtered_df[filtered_df['hr'] != 0].copy()
    print(f"Row count after dropping artifacts and baselines: {len(filtered_df)}")

    # --- Step 3: Parse pipe-separated columns ---
    # Helper function to safely parse the pipe-separated strings
    def parse_pipe_string(val, cast_type):
        # Catch literal NaN floats immediately
        if pd.isna(val):
            return []
        
        # Convert to string and clean it up
        val_str = str(val).strip()
        
        # Check for empty strings or string versions of nulls
        if val_str in ('', 'nan', 'None'):
            return []
            
        # Split by pipe and cast each value to the correct type (int or float)
        return [cast_type(v) for v in val_str.split('|') if v.strip()]

    # Parse IBI strings into lists of integers
    filtered_df['ibi'] = filtered_df['ibi'].apply(lambda x: parse_pipe_string(x, int))
    
    # Parse ECG strings into lists of floats
    filtered_df['ecg'] = filtered_df['ecg'].apply(lambda x: parse_pipe_string(x, float))

    print("\nFirst 3 rows of parsed data:")
    print(filtered_df[['hr', 'ibi', 'pain_level', 'ecg', 'person_id']].head(3))
    
    # --- Step 4: Handle missing IBI ---
    # Pandas ffill/bfill methods look for 'NaN' (Not a Number), not empty lists. 
    # So first, we swap any empty lists [] with np.nan
    filtered_df['ibi'] = filtered_df['ibi'].apply(lambda x: np.nan if len(x) == 0 else x)
    
    # Forward-fill uses the previous row's value. 
    # Back-fill runs right after to catch edge cases (like if the very first row is empty).
    filtered_df['ibi'] = filtered_df['ibi'].ffill().bfill()
    
    print("\n--- Step 4 Complete ---")
    print("Missing IBI rows successfully forward/back-filled.")

    # --- Step 5: Group into pain events ---
    # We create a new event group every time the pain_level changes.
    # We ALSO trigger a new group if the person_id changes, so two people's 
    # data don't accidentally merge together if they both logged the same pain level.
    event_change = (filtered_df['pain_level'] != filtered_df['pain_level'].shift()) | \
                   (filtered_df['person_id'] != filtered_df['person_id'].shift())
    
    filtered_df['event_id'] = event_change.cumsum()
    
    print("\n--- Step 5 Complete ---")
    print(f"Created {filtered_df['event_id'].nunique()} distinct pain events.")
    
    # Let's do a quick sanity check to see if our events are actually ~30 rows long
    avg_rows = filtered_df['event_id'].value_counts().mean()
    print(f"Average rows per event: {avg_rows:.1f} (Target is ~30)")
    
    # --- Step 6: Feature extraction per event ---
    def extract_features(event_df):
        # 1. HR Features
        hr_mean = event_df['hr'].mean()
        hr_std = event_df['hr'].std()
        hr_min = event_df['hr'].min()
        hr_max = event_df['hr'].max()
        hr_range = hr_max - hr_min
        
        # 2. IBI Features - Flatten all IBI lists in this window into one long list
        all_ibis = [val for sublist in event_df['ibi'] for val in sublist]
        
        if len(all_ibis) > 1:
            ibi_mean = np.mean(all_ibis)
            ibi_std = np.std(all_ibis)
            ibi_min = np.min(all_ibis)
            ibi_max = np.max(all_ibis)
            ibi_range = ibi_max - ibi_min
            
            # First and second differences (Heart Rate Variability signals)
            ibis_array = np.array(all_ibis)
            first_diffs = np.abs(np.diff(ibis_array))
            ibi_first_diff_mean = np.mean(first_diffs)
            
            if len(first_diffs) > 1:
                second_diffs = np.abs(np.diff(first_diffs))
                ibi_second_diff_mean = np.mean(second_diffs)
            else:
                ibi_second_diff_mean = 0.0
        else:
            # Fallback if an event somehow has no valid IBI data
            ibi_mean = ibi_std = ibi_min = ibi_max = ibi_range = 0.0
            ibi_first_diff_mean = ibi_second_diff_mean = 0.0
            
        # 3. ECG Feature - Mean of the 10 values (same across window, take first row)
        first_ecg = event_df['ecg'].iloc[0]
        ecg_mean = np.mean(first_ecg) if len(first_ecg) > 0 else 0.0
        
        # 4. Labels and Metadata
        pain_level = event_df['pain_level'].iloc[0]
        person_id = event_df['person_id'].iloc[0]
        
        # Return as a pandas Series to form our new DataFrame row
        return pd.Series({
            'hr_mean': hr_mean, 'hr_std': hr_std, 'hr_min': hr_min, 
            'hr_max': hr_max, 'hr_range': hr_range,
            'ibi_mean': ibi_mean, 'ibi_std': ibi_std, 'ibi_min': ibi_min, 
            'ibi_max': ibi_max, 'ibi_range': ibi_range,
            'ibi_first_diff_mean': ibi_first_diff_mean,
            'ibi_second_diff_mean': ibi_second_diff_mean,
            'ecg_mean': ecg_mean,
            'pain_level': pain_level,
            'person_id': person_id
        })

    print("\n--- Extracting Features (This might take a second)... ---")
    # Apply the feature extraction to each grouped event
    features_df = filtered_df.groupby('event_id').apply(extract_features).reset_index(drop=True)
    
    # Fill any potential NaNs (like if an event was too short to calculate standard deviation)
    features_df = features_df.fillna(0)
    
    print("\n--- Step 6 Complete ---")
    print(f"Distilled down to {len(features_df)} final feature rows.")
    
    # Change the return statement to output our new feature matrix!
    return features_df
    
if __name__ == "__main__":
    df = load_and_clean_data()
    
    if df is not None:
        

        # --- Step 8: Save cleaned dataset ---
        output_path = os.path.join(BASE_DIR, 'processed_data.csv')
        df.to_csv(output_path, index=False)
        
        print("\n--- Step 8 Complete ---")
        print(f"Final feature matrix saved to {output_path}")
        
        # Verify class balance so we know if anyone is missing high/low pain data
        print("\n--- Class Balance (Pain Levels per Person) ---")
        balance = df.groupby(['person_id', 'pain_level']).size().unstack(fill_value=0)
        print(balance)
        
        print("\nData pipeline finished! Ready for PyTorch.")