import requests
import sseclient

# Server URL
BASE_URL = "http://127.0.0.1:5000"

# User data for signup and login
user_data = {
    "email": "testuser@example.com",
    "password": "securepassword123"
}

# Dustbin data
dustbin_data = {
    "dustbin_id": "DB123",
    "location": "Street 123, City"
}

# Start a session
session = requests.Session()

# 1. User Signup
def signup():
    response = session.post(f"{BASE_URL}/signup", json=user_data)
    if response.status_code == 201:
        print("Signup successful.")
    elif response.status_code == 409:
        print("User already exists.")
    else:
        print("Signup failed:", response.json())

# 2. User Login
def login():
    response = session.post(f"{BASE_URL}/login", json=user_data)
    if response.status_code == 200:
        print("Login successful.")
        return True
    else:
        print("Login failed:", response.json())
        return False

# 3. Add Dustbin
def add_dustbin():
    response = session.post(f"{BASE_URL}/add_dustbin", json=dustbin_data)
    if response.status_code == 201:
        print("Dustbin added successfully.")
    else:
        print("Failed to add dustbin:", response.json())

# 4. List All Dustbins
def list_dustbins():
    response = session.get(f"{BASE_URL}/dustbins")
    if response.status_code == 200:
        dustbins = response.json()
        print("List of dustbins:")
        for dustbin in dustbins:
            print(dustbin)
            # print(f"Dustbin ID: {dustbin['dustbin_id']}, State: {dustbin['state']}, Location: {dustbin.get('location')}")
    else:
        print("Failed to list dustbins:", response.json())

# 5. Listen for SSE Notifications
def listen_for_notifications():
    print("Listening for SSE notifications...")
    with session.get(f"{BASE_URL}/notifications", stream=True) as response:
        client = sseclient.SSEClient(response)
        
        for event in client.events():
            print("Received event:", event.data)

# Running the client sequence
if __name__ == "__main__":
    signup()  # Step 1: Sign up the user
    if login():  # Step 2: Log in the user
        add_dustbin()  # Step 3: Add a dustbin for the user
        list_dustbins()  # Step 4: List all dustbins for the user
        listen_for_notifications()  # Step 5: Start listening for notifications
