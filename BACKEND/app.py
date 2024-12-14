import os
import json
import hmac
import hashlib
from flask import Flask, request, jsonify, session, Response, stream_with_context
from werkzeug.security import generate_password_hash, check_password_hash
import time, requests
from dotenv import load_dotenv

# Flask App Setup
app = Flask(__name__)
app.secret_key = "your_secret_key"  # Replace with a secure key
load_dotenv()
# In-memory databases and file paths
USERS_FILE = "users_db.json"
DUSTBINS_FILE = "dustbins_db.json"
PAYSTACK_SECRET_KEY = os.getenv("paystack_live")
users_db = {}
dustbins_db = {}
notifications = {}  # In-memory notifications storage


# Load data from JSON files at startup
def load_data(file_path):
    """Load data from a JSON file."""
    if not os.path.exists(file_path):
        return {}
    with open(file_path, "r") as file:
        return json.load(file)


def save_data(file_path, data):
    """Save data to a JSON file."""
    with open(file_path, "w") as file:
        json.dump(data, file, indent=4)


def save_db(db_name):
    """Save the specific database that changes."""
    if db_name == "users":
        save_data(USERS_FILE, users_db)
    elif db_name == "dustbins":
        save_data(DUSTBINS_FILE, dustbins_db)
    else:
        raise ValueError(f"Unknown database: {db_name}")


# Load databases at startup
users_db = load_data(USERS_FILE)
dustbins_db = load_data(DUSTBINS_FILE)


# Routes
@app.route("/signup", methods=["POST"])
def signup():
    data = request.json
    if data["email"] in users_db:
        return jsonify({"error": "User already exists"}), 409

    hashed_password = generate_password_hash(data["password"])
    users_db[data["email"]] = {"password": hashed_password, "dustbins": []}
    save_db("users")  # Save only the users database
    return jsonify({"message": "User created successfully"}), 201


@app.route("/login", methods=["POST"])
def login():
    data = request.json
    user = users_db.get(data["email"])
    if user and check_password_hash(user["password"], data["password"]):
        session["user_id"] = data["email"]
        return jsonify({"message": "Logged in successfully"}), 200
    return jsonify({"error": "Invalid credentials"}), 401


@app.route("/logout", methods=["POST"])
def logout():
    session.pop("user_id", None)
    return jsonify({"message": "Logged out successfully"}), 200


@app.route("/add_dustbin", methods=["POST"])
def add_dustbin():
    user_id = session.get("user_id")
    if not user_id:
        return jsonify({"error": "Unauthorized"}), 403

    data = request.json
    dustbin_id = data["dustbin_id"]
    dustbins_db[dustbin_id] = {
        "user_id": user_id,
        "dustbin_id": dustbin_id,
        "location": data.get("location"),
        "state": "empty",
        "reference_pay": None,
    }
    # users_db[user_id]["dustbins"].append(dustbin_id)
    save_db("dustbins")  # Save only the dustbins database
    # save_db("users")  # Save users since dustbins list for user is updated
    return jsonify({"message": "Dustbin added"}), 201


@app.route("/dustbins", methods=["GET"])
def list_dustbins():
    user_id = session.get("user_id")
    if not user_id:
        return jsonify({"error": "Unauthorized"}), 403


    # Filter dustbins directly based on the `user_id`
    user_dustbins = [
        dustbin for dustbin in dustbins_db.values() if dustbin["user_id"] == user_id
    ]

    return jsonify(user_dustbins), 200

    # user_dustbins = [
    #     dustbins_db[dustbin_id] for dustbin_id in users_db[user_id]["dustbins"]
    # ]
    # return jsonify(user_dustbins), 200


@app.route("/dustbin_state", methods=["POST"])
def update_dustbin_state():
    data = request.json
    dustbin_id = data.get("dustbin_id")
    state = data.get("state")

    if dustbin_id in dustbins_db:
        dustbins_db[dustbin_id]["state"] = state
        user_id = dustbins_db[dustbin_id].get("user_id", None)
        save_db("dustbins")  # Save only the dustbins database
        print(user_id, "jjjjjj")
        if user_id:
            if user_id not in notifications:
                notifications[user_id] = []
            notifications[user_id].append(
                {
                    "message": "Dustbin state updated",
                    "dustbin_id": dustbin_id,
                    "state": state,
                }
            )

        return jsonify({"message": "Dustbin state updated"}), 200
    else:

        dustbins_db[dustbin_id] = {
            "state": state,
        }
        save_db("dustbins")
        user_id = None
    print(user_id, "jjjjjj")
    if user_id:
        if user_id not in notifications:
            notifications[user_id] = []
        notifications[user_id].append(
            {
                "message": "Dustbin state updated",
                "dustbin_id": dustbin_id,
                "state": state,
            }
        )

    return jsonify({"message": "Dustbin just  added"}), 200


@app.route("/notifications", methods=["GET"])
def get_notifications():
    user_id = session.get("user_id")
    if not user_id:
        return jsonify({"error": "Unauthorized"}), 403

    #     user_notifications = notifications.get(user_id, [])
    #     notifications[user_id] = []  # Clear notifications after retrieval
    #     return jsonify(user_notifications), 200
    # @app.route("/notifications")
    # @stream_with_context
    # def notifications():
    #     user_id = session.get("user_id")
    #     if not user_id:
    # return jsonify({"error": "Unauthorized"}), 403

    def event_stream():
        # Listen for notifications from in-memory storage (notifications dict)
        while True:
            # Get notifications for the current user
            user_notifications = notifications.get(user_id, [])
            # if user_notifications:

            # print(user_notifications)
            if user_notifications:
                # print("rrrrrrrrrrrrrrrrrr")

                # Send each notification as an SSE event
                # for notification in user_notifications:
                yield f"data: {json.dumps(user_notifications)}\n\n"
                # print(user_notifications)
                notifications[user_id] = []
                # time.sleep(2)
            else:
                # print("here")
                time.sleep(3)
                # pass
                # continue
            # else:
            #     time.sleep(3)
            # Clear notifications after sending to avoid duplicates

    return Response(event_stream(), content_type="text/event-stream")


@app.route("/payment_webhook", methods=["POST"])
def payment_webhook():
    signature = request.headers.get("x-paystack-signature")
    if not signature:
        return jsonify({"error": "No signature provided"}), 403

    payload = request.get_data()
    computed_signature = hmac.new(
        os.getenv("PAYSTACK_SECRET_KEY", "").encode("utf-8"), payload, hashlib.sha512
    ).hexdigest()

    if computed_signature != signature:
        return jsonify({"error": "Invalid signature"}), 403

    data = request.json
    event_type = data.get("event")

    if event_type == "charge.success":
        reference = data["data"].get("reference")
        for dustbin_id, dustbin in dustbins_db.items():
            if dustbin.get("reference_pay") == reference:
                dustbin["reference_pay"] = None
                user_id = dustbin["user_id"]
                if user_id not in notifications:
                    notifications[user_id] = []
                notifications[user_id].append(
                    {"message": "Payment received. Trash will be picked up soon."}
                )
                save_db("dustbins")  # Save only the dustbins database
                break
    return jsonify({"status": "ok"}), 200


@app.route("/woww", methods=["GET"])
def home():
    return Response("I AM WORKING ")


def initiate_payment(amount, email, phone, provider, dustbin):
    url = "https://api.paystack.co/charge"
    headers = {
        "Authorization": f"Bearer {PAYSTACK_SECRET_KEY}",
        "Content-Type": "application/json",
    }
    payload = {
        "amount": amount,
        "email": email,
        "currency": "KES",
        "dustbin_id": dustbin,
        "mobile_money": {
            "phone": phone,
            "provider": provider,
        },
    }
    response = requests.post(url, json=payload, headers=headers)
    return (
        response.json()
        if response.status_code == 200
        else {"error": "Payment initiation failed", "details": response.json()}
    )


@app.route("/payment_start", methods=["POST"])
def payment_processing():
    data = request.json
    user_email = data.get("email")
    dustbin_id = data.get("dustbin_id")
    phone_number = data.get("phone")

    provider = "mpesa"  # Mobile money provider

    payment_response = initiate_payment(
        300, user_email, phone_number, provider, dustbin_id
    )
    # user_id = db.dustbins.find_one({"dustbin_id": dustbin_id}).get("user_id")
    user_id = dustbins_db.get(dustbin_id, {}).get("user_id")
    print(payment_response)
    # _logger.info(payment_response)
    if user_id:
        if "error" not in payment_response:
            data = payment_response["data"]
            print(data)

            if data.get("status") == "pay_offline":
                reference = data["reference"]
                # db.dustbins.update_one(
                #     {"dustbin_id": dustbin_id}, {"$set": {"reference_pay": reference}}
                # )
                dustbins_db[dustbin_id]["reference_pay"] = reference
                save_db("dustbins")
            else:
                return (
                    jsonify(
                        {
                            "error": "Payment initiation failed",
                            "details": payment_response,
                        }
                    ),
                    500,
                )
        else:
            print(payment_response)
            return (
                jsonify(
                    {"error": "Payment initiation failed", "details": payment_response}
                ),
                500,
            )

    return jsonify({"message": "Payment suceesful"}), 200


# Run the app
if __name__ == "__main__":
    app.run(debug=True)

"""
UNIQUE DUSTBIN ID
UNIQUE USER ID 
"""
