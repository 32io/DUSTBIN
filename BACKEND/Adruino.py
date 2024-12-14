import requests  # Only for demonstration, will not run in this environment
import random
import time

class ADRUINO:
    def __init__(self, dustbin_id, capacity=100):
        self.dustbin_id = dustbin_id
        self.capacity = capacity
        self.current_trash_level = 0
        self.previous_trash_level = None
        self.server_url = "http://127.0.0.1:5000"  # Replace with actual server URL

    def register_dustbin(self):
        """
        Register the dustbin with the server via HTTP.
        """
        registration_data = {
            "dustbin_id": self.dustbin_id,
            "capacity": self.capacity
        }
        try:
            response = requests.post(f"{self.server_url}/register_dustbin", json=registration_data)
            print(f"Registered dustbin {self.dustbin_id}. Server response: {response.status_code}")
        except Exception as e:
            print(f"Failed to register dustbin: {e}")

    def update_trash_level(self, added_trash):
        """
        Simulate adding trash to the dustbin, updating the trash level.
        """
        if self.current_trash_level + added_trash <= self.capacity:
            self.current_trash_level += added_trash
            print(f"Added {added_trash} units of trash. Current level: {self.current_trash_level}/{self.capacity}")
        else:
            self.current_trash_level = self.capacity
            print("Dustbin is full!")

    def send_data_update(self):
        """
        Send an update to the server if there's a change in the trash level.
        """
        if self.current_trash_level != self.previous_trash_level:
            status_data = {
                "dustbin_id": self.dustbin_id,
                # "trash_level": self.current_trash_level,
                "state": f"full-{self.capacity}" if self.current_trash_level >= self.capacity else  self.current_trash_level
            }
            try:
                response = requests.post(f"{self.server_url}/dustbin_state", json=status_data)
                print(f"Sent update. Server response: {response.status_code}")
            except Exception as e:
                print(f"Failed to send data: {e}")
            # Update the previous trash level to avoid duplicate sends
            self.previous_trash_level = self.current_trash_level
        else:
            print("No change in trash level. Skipping data send.")

# Simulation

dustbin = ADRUINO(dustbin_id="DB123", capacity=100)

# Step 1: Register the dustbin
dustbin.register_dustbin()

# Step 2: Simulate adding trash and sending updates
for _ in range(10):
    print("here")
    added_trash = random.randint(10, 30)  # Random amount of trash added
    dustbin.update_trash_level(added_trash)
    dustbin.send_data_update()
    time.sleep(1)  # Pause to simulate real-time behavior

    if dustbin.current_trash_level >= dustbin.capacity:
        print("Dustbin reached capacity. Stopping simulation.")
        break
