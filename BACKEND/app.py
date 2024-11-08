import requests
from flask import Flask, request, jsonify, session, Response, stream_with_context
from pymongo import MongoClient
from werkzeug.security import generate_password_hash, check_password_hash
from bson.json_util import dumps
import redis
import json
import time

# Setup Flask app
app = Flask(__name__)
app.secret_key = "your_secret_key"  # Change to a more secure secret

# Initialize MongoDB
mongo = MongoClient("mongodb://localhost:27017/trash_mgmt")
mongo.db.dustbins.create_index([("email", 1)], unique=True)

# Initialize Redis Client
redis_client = redis.StrictRedis(host='localhost', port=6379, decode_responses=True)

# Paystack Integration
PAYSTACK_SECRET_KEY = "your_paystack_secret_key"  # Replace with your actual Paystack secret key


# Function to call Paystack's Mobile Money API
def initiate_payment(amount, email, phone, provider):
    url = "https://api.paystack.co/charge"
    headers = {
        "Authorization": f"Bearer {PAYSTACK_SECRET_KEY}",
        "Content-Type": "application/json",
    }
    payload = {
        "amount": amount,
        "email": email,
        "currency": "GHS",
        "mobile_money": {
            "phone": phone,
            "provider": provider,
        },
    }
    response = requests.post(url, json=payload, headers=headers)
    return response.json() if response.status_code == 200 else {"error": "Payment initiation failed", "details": response.json()}


# Routes

# 1. User Signup
@app.route("/signup", methods=["POST"])
def signup():
    data = request.json
    existing_user = mongo.db.users.find_one({"email": data["email"]})
    if existing_user:
        return jsonify({"error": "User already exists"}), 409

    hashed_password = generate_password_hash(data["password"])
    mongo.db.users.insert_one({"email": data["email"], "password": hashed_password})
    return jsonify({"message": "User created successfully"}), 201


# 2. User Login
@app.route("/login", methods=["POST"])
def login():
    data = request.json
    user = mongo.db.users.find_one({"email": data["email"]})
    if user and check_password_hash(user["password"], data["password"]):
        session["user_id"] = str(user["_id"])
        return jsonify({"message": "Logged in successfully"}), 200
    return jsonify({"error": "Invalid credentials"}), 401


# 3. User Logout
@app.route("/logout", methods=["POST"])
def logout():
    session.pop("user_id", None)
    return jsonify({"message": "Logged out successfully"}), 200


# 4. Add Dustbin
@app.route("/add_dustbin", methods=["POST"])
def add_dustbin():
    user_id = session.get("user_id")
    if not user_id:
        return jsonify({"error": "Unauthorized"}), 403

    data = request.json
    mongo.db.dustbins.update_one(
        {"dustbin_id": data["dustbin_id"]},
        {
            "$set": {
                "user_id": user_id,
                "dustbin_id": data["dustbin_id"],
                "state": "empty",
                "location": data.get("location"),
            }
        },
        upsert=True,
    )
    return jsonify({"message": "Dustbin added"}), 201


# 5. List Dustbins
@app.route("/dustbins", methods=["GET"])
def list_dustbins():
    user_id = session.get("user_id")
    if not user_id:
        return jsonify({"error": "Unauthorized"}), 403

    dustbins = list(mongo.db.dustbins.find({"user_id": user_id}))
    return Response(dumps(dustbins), mimetype="application/json")


# 6. Update Dustbin State
@app.route("/dustbin_state", methods=["POST"])
def update_dustbin_state():
    data = request.json
    dustbin_id = data.get("dustbin_id")
    state = data.get("state")

    mongo.db.dustbins.update_one(
        {"dustbin_id": dustbin_id},
        {"$set": {"state": state}},
    )

    # Publish message to Redis channel
    user_id = mongo.db.dustbins.find_one({"dustbin_id": dustbin_id}).get("user_id")
    if state == "full":
        user_email = "customer@email.com"  # Replace with actual user email
        phone_number = "+254701860614"  # Replace with actual user phone number
        provider = "mpesa"  # Mobile money provider

        payment_response = initiate_payment(100, user_email, phone_number, provider)
        if "error" not in payment_response:
            pay_url = payment_response["data"].get("authorization_url", "")
            mongo.db.pending_payments.insert_one(
                {"user_id": user_id, "dustbin_id": dustbin_id, "payment_url": pay_url}
            )
            redis_client.publish(user_id, json.dumps({"message": "Payment pending", "payment_url": pay_url}))
        else:
            return jsonify({"error": "Payment initiation failed", "details": payment_response}), 500
    else:
        redis_client.publish(user_id, json.dumps({"message": "Dustbin state updated", "state": state}))

    return jsonify({"message": "Dustbin state updated"}), 200


# 7. SSE Notification Stream
@app.route("/notifications")
@stream_with_context
def notifications():
    user_id = session.get("user_id")
    if not user_id:
        return jsonify({"error": "Unauthorized"}), 403

    def event_stream():
        pubsub = redis_client.pubsub()
        pubsub.subscribe(user_id)  # Subscribes to the user's unique channel
        for message in pubsub.listen():
            if message["type"] == "message":
                yield f"data: {message['data']}\n\n"

    return Response(event_stream(), content_type="text/event-stream")


if __name__ == "__main__":
    app.run(debug=True)
