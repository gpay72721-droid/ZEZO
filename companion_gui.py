"""Simple desktop companion to open JSON report and show summary.
Requires: pip install tkinter (usually builtin), pillow
Usage: python companion_gui.py report.json
"""
import json, sys
from tkinter import Tk, Label, Button, filedialog, messagebox

def load_report(path):
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)

root = Tk()
root.title('MFD Companion')
root.geometry('600x400')

label = Label(root, text='Mobile Fault Detector - Companion')
label.pack()

def open_report():
    p = filedialog.askopenfilename(filetypes=[('JSON files','*.json')])
    if not p: return
    try:
        r = load_report(p)
        summary = '\n'.join([f"{x['name']}: {x['status']}" for x in r])
        messagebox.showinfo('Report Summary', summary)
    except Exception as e:
        messagebox.showerror('Error', str(e))

btn = Button(root, text='Open JSON Report', command=open_report)
btn.pack(pady=20)

root.mainloop()
