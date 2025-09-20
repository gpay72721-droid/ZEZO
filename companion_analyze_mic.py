"""Analyze microphone test recording and estimate SNR.
Requires: pip install soundfile numpy
Usage: python companion_analyze_mic.py path/to/mic_test.wav
"""
import sys
import numpy as np
import soundfile as sf

def estimate_snr(path):
    data, sr = sf.read(path)
    if data.ndim > 1:
        data = data.mean(axis=1)
    rms = np.sqrt(np.mean(data**2))
    # naive noise estimate: use 0.5s from start as noise floor
    n = int(min(len(data), int(0.5*sr)))
    noise_floor = np.sqrt(np.mean(data[:n]**2)) if n>0 else 1e-9
    snr_db = 20 * np.log10((rms+1e-12) / (noise_floor+1e-12))
    return snr_db

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python companion_analyze_mic.py mic_test.wav')
        sys.exit(1)
    p = sys.argv[1]
    snr = estimate_snr(p)
    print(f'Estimated SNR (dB): {snr:.2f}')
