import json
import base64
from cryptography.hazmat.primitives import serialization

with open("C:/Users/HLadmin/AndroidStudioProjects/ContactsSyncApp/app/src/main/res/raw/credentials.json", "r") as f:
    data = json.load(f)
    key_str = data["private_key"]
    print(f"Key starts with: {key_str[:50]}")
    try:
        key = serialization.load_pem_private_key(key_str.encode(), password=None)
        print("Successfully loaded private key using cryptography library")
    except Exception as e:
        print(f"Failed to load private key: {e}")
