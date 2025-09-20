"""ML scaffold: prepares synthetic dataset, trains a simple LightGBM/XGBoost-like model,
and saves a placeholder model file. This is a scaffold for further training with real data.
"""
import json
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
import joblib

# create synthetic dataset
np.random.seed(0)
N = 1000
X = pd.DataFrame({
    'battery_pct': np.random.randint(1,100,size=N),
    'cpu_temp': np.random.normal(40,5,size=N),
    'storage_free_pct': np.random.randint(1,100,size=N),
    'crash_count_24h': np.random.poisson(0.2, size=N),
    'mic_snr': np.random.normal(30,5,size=N)
})
# synthetic labels: hardware_issue (battery/camera/speaker) vs software_fixable
y = ( (X['battery_pct']<15) | (X['crash_count_24h']>2) ).astype(int)
model = RandomForestClassifier(n_estimators=100, random_state=0)
model.fit(X, y)
joblib.dump(model, 'mfd_model_rf.joblib')
print('Trained synthetic model saved to mfd_model_rf.joblib')
