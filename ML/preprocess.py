
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
    
    # --- Step 6: Sliding Window Feature Extraction ---
    WINDOW_SIZE = 10      # rows per window
    STEP_SIZE = 1         # how many rows to slide each time

    def extract_features_from_window(window_df):
        hr = window_df['hr'].values
        all_ibis = [val for sublist in window_df['ibi'] for val in sublist]
        
        hr_mean = np.mean(hr)
        hr_std = np.std(hr)
        hr_min = np.min(hr)
        hr_max = np.max(hr)
        hr_range = hr_max - hr_min

        if len(all_ibis) > 1:
            ibis_array = np.array(all_ibis)
            ibi_mean = np.mean(ibis_array)
            ibi_std = np.std(ibis_array)
            ibi_min = np.min(ibis_array)
            ibi_max = np.max(ibis_array)
            ibi_range = ibi_max - ibi_min
            first_diffs = np.abs(np.diff(ibis_array))
            ibi_first_diff_mean = np.mean(first_diffs)
            ibi_second_diff_mean = np.mean(np.abs(np.diff(first_diffs))) if len(first_diffs) > 1 else 0.0
            
            # RMSSD - root mean square of successive differences, key HRV metric
            rmssd = np.sqrt(np.mean(np.diff(ibis_array) ** 2))
            
            # pNN50 - proportion of successive IBI differences > 50ms
            pnn50 = np.sum(np.abs(np.diff(ibis_array)) > 50) / len(ibis_array)
        else:
            ibi_mean = ibi_std = ibi_min = ibi_max = ibi_range = 0.0
            ibi_first_diff_mean = ibi_second_diff_mean = rmssd = pnn50 = 0.0

        # ECG variance instead of mean - captures waveform shape better
        first_ecg = window_df['ecg'].iloc[0]
        ecg_var = np.var(first_ecg) if len(first_ecg) > 0 else 0.0

        return {
            'hr_mean': hr_mean, 'hr_std': hr_std, 'hr_min': hr_min,
            'hr_max': hr_max, 'hr_range': hr_range,
            'ibi_mean': ibi_mean, 'ibi_std': ibi_std, 'ibi_min': ibi_min,
            'ibi_max': ibi_max, 'ibi_range': ibi_range,
            'ibi_first_diff_mean': ibi_first_diff_mean,
            'ibi_second_diff_mean': ibi_second_diff_mean,
            'rmssd': rmssd, 'pnn50': pnn50,
            'ecg_var': ecg_var
        }

    print("\n--- Step 6: Sliding Window Feature Extraction ---")
    all_windows = []

    for event_id, event_df in filtered_df.groupby('event_id'):
        event_df = event_df.reset_index(drop=True)
        pain_level = event_df['pain_level'].iloc[0]
        person_id = event_df['person_id'].iloc[0]
        
        # slide window across the event
        for start in range(0, len(event_df) - WINDOW_SIZE + 1, STEP_SIZE):
            window = event_df.iloc[start:start + WINDOW_SIZE]
            features = extract_features_from_window(window)
            features['pain_level'] = pain_level
            features['person_id'] = person_id
            all_windows.append(features)

    features_df = pd.DataFrame(all_windows).fillna(0)
    print(f"Events: {filtered_df['event_id'].nunique()} → Windows: {len(features_df)}")
    
    print("\n--- Step 6 Complete ---")
    
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