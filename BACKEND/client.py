import requests
import sseclient
import json
import time
# Server URL
BASE_URL = "http://localhost:5000"

# User data for signup and login
user_data = {
    "email": "testuser@example.com",
    "password": "securepassword123"
}
phone_number = "+254701860614"
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

def initiate_payment(dustbin_id):
    payment_data = {
        "email": user_data["email"],
        "dustbin_id": dustbin_id,
        "phone": phone_number
    }
    response = session.post(f"{BASE_URL}/payment_start", json=payment_data)
    if response.status_code == 200:
        print("Payment initiated successfully.")
        print("Response:", response.json())
    else:
        print("Payment initiation failed:", response.json())
# 5. Listen for SSE Notifications
def listen_for_notifications():
    print("Listening for SSE notifications...")
    while True:
        
        try:
            # Make a GET request to the notifications endpoint
            response = session.get(f"{BASE_URL}/notifications", timeout=10)
            response.raise_for_status()  # Raise an HTTPError if the response has an error status

            # Parse the notifications received
            notifications = response.json()  # Assuming the response is JSON
            for notification_data in notifications:
                # Check if the notification is about a "full" dustbin state
                if "full" in str(notification_data.get("state")):
                    print(f"Dustbin {notification_data.get('dustbin_id')} is full. Initiating payment...")
                    initiate_payment(notification_data.get("dustbin_id"))
                else:
                    print("Notification received:", notification_data)
        except requests.exceptions.RequestException as req_err:
            print(f"Request error: {req_err}")
        except json.JSONDecodeError as json_err:
            print(f"JSON parsing error: {json_err}")
        except Exception as e:
            print(f"Error processing notification: {e}")

        # Wait for 10 seconds before making the next request
        time.sleep(10)
# Running the client sequence
if __name__ == "__main__":
    signup()  # Step 1: Sign up the user
    if login():  # Step 2: Log in the user
        add_dustbin()  # Step 3: Add a dustbin for the user
        list_dustbins()  # Step 4: List all dustbins for the user
        listen_for_notifications()  # Step 5: Start listening for notifications
