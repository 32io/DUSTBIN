import requests
from flask import Flask, request, jsonify, session, Response, stream_with_context
from pymongo import MongoClient
from werkzeug.security import generate_password_hash, check_password_hash
from bson.json_util import dumps
import redis
import json
import time
import os
import hmac
import hashlib
from dotenv import load_dotenv

load_dotenv()
# Setup Flask app
app = Flask(__name__)
app.secret_key = os.getenv("SECRET_KEY", "your_secret_key")  # Change to a more secure secret

# Initialize MongoDB
mongo = MongoClient("mongodb://localhost:27017/trash_mgmt")
mongo.db.dustbins.create_index([("dustbin_id", 1)], unique=True)

# Initialize Redis Client
redis_client = redis.StrictRedis(host='localhost', port=6379, decode_responses=True)

# Paystack Integration
PAYSTACK_SECRET_KEY = os.getenv("paystack_live")  # Replace with your actual Paystack secret key


# Function to call Paystack's Mobile Money API
def initiate_payment(amount, email, phone, provider,dustbin):
    url = "https://api.paystack.co/charge"
    headers = {
        "Authorization": f"Bearer {PAYSTACK_SECRET_KEY}",
        "Content-Type": "application/json",
    }
    payload = {
        "amount": amount,
        "email": email,
        "currency": "KES",
        "dustbin_id":dustbin,
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
    session.pop("email", None)
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
                "user_id": session.get("email"),  # Use email as user identifier
                "dustbin_id": data["dustbin_id"],
                "state": "0",  # Initial state as 0%
                "location": data.get("location"),
            }
        },
        upsert=True,
    )
    return jsonify({"message": "Dustbin added"}), 201
    
# New endpoint for bin status
@app.route("/bin/status", methods=["GET"])
def get_bin_status():
    user_email = request.args.get("userId")
    if not user_email:
        return jsonify({"error": "User ID required"}), 400
    
    dustbin = mongo.db.dustbins.find_one({"user_id": user_email})
    if not dustbin:
        return jsonify({"error": "No dustbin found"}), 404
    
    return jsonify({
        "binId": dustbin.get("dustbin_id", ""),
        "fillLevel": int(dustbin.get("state", 0)) if str(dustbin.get("state", "0")).isdigit() else 0,
        "state": str(dustbin.get("state", "0"))
    })    
# 5. List Dustbins
@app.route("/dustbins", methods=["GET"])
def list_dustbins():
    user_id = session.get("user_id")
    if not user_id:
        return jsonify({"error": "Unauthorized"}), 403

    dustbins = list(mongo.db.dustbins.find({"user_id": session.get("email")}))
    return Response(dumps(dustbins), mimetype="application/json")

    

@app.route("/", methods=["GET"])
def home():
    return Response("I AM WORKING ")
@app.route("/payment_start", methods=["POST"])
def payment_processing():
    data = request.json
    user_email = data.get("email")
    dustbin_id = data.get("dustbin_id")
    phone_number=data.get("phone")

    provider = "mpesa"  # Mobile money provider

    payment_response = initiate_payment(300, user_email, phone_number, provider,dustbin_id)
    user_id = mongo.db.dustbins.find_one({"dustbin_id": dustbin_id}).get("user_id")
    if user_id:
        if "error" not in payment_response:
            data = payment_response["data"]
            print(data)
                

            if data.get("status")=="pay_offline":
                reference=data["reference"]
                mongo.db.dustbins.update_one({"dustbin_id": dustbin_id},{"$set":{
                    "reference_pay":reference
                }})
                # mongo.db.pending_payments.insert_one(
                #     {"user_id": user_id, "dustbin_id": dustbin_id, "payment_url": pay_url}
                # )""
                redis_client.publish(user_id, json.dumps({"message": "Payment pending", "payment_state":"Pending","display":data["display_text"]}))
            # print(reference)
            # if reference:
            else:
                return jsonify({"error": "Payment initiation failed", "details": payment_response}), 500
        else:
            print(payment_response)
            return jsonify({"error": "Payment initiation failed", "details": payment_response}), 500

    return jsonify({"message": "Payment suceesful"}), 200

# 6. Update Dustbin State
@app.route("/dustbin_state", methods=["POST"])
def update_dustbin_state():
    data = request.json
    dustbin_id = data.get("dustbin_id")
    state = data.get("state")

    if not dustbin_id or state is None:
        return jsonify({"error": "Missing dustbin_id or state"}), 400

    # Ensure state is converted to a string
    state_str = str(state)

    # Update dustbin in MongoDB
    result = mongo.db.dustbins.update_one(
        {"dustbin_id": dustbin_id},
        {"$set": {"state": state_str}},
    )


 # Publish message to Redis channel  
dustbin = mongo.db.dustbins.find_one({"dustbin_id": dustbin_id})
    if dustbin:
        user_id = dustbin.get("user_id")
        redis_client.publish(user_id, json.dumps({
            "message": "Dustbin state updated",
            "dustbin_id": dustbin_id, 
            "state": state_str
        }))
    
    # Send a clear, explicit response
    return jsonify({
        "message": "Dustbin state updated successfully", 
        "dustbin_id": dustbin_id, 
        "state": state_str
    }), 200

@app.route("/register_dustbin", methods=["POST"])
def register_dustbin():
    data = request.json
    mongo.db.dustbins.update_one(
        {"dustbin_id": data["dustbin_id"]},
        {"$set": {"state": data.get("state", "empty")}},
        upsert=True,
    )
    return jsonify({"message": "Dustbin registered successfully"}), 200


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

# Webhook route to handle Paystack events
@app.route("/webhook", methods=["POST"])
def payment_webhook():
    # Step 1: Verify signature to ensure the request is from Paystack
    signature = request.headers.get("x-paystack-signature")
    if not signature:
        return jsonify({"error": "No signature provided"}), 403

    # Calculate HMAC SHA512 signature using your Paystack secret key
    payload = request.get_data()

    computed_signature = hmac.new(
        PAYSTACK_SECRET_KEY.encode("utf-8"),
        payload,
        hashlib.sha512
    ).hexdigest()

    if computed_signature != signature:
        return jsonify({"error": "Invalid signature"}), 403

    # Step 2: Process the webhook event
    data = request.json
    print(request.json)
    event_type = data.get("event")

    # Handle 'charge.success' events
    if event_type == "charge.success":
        # Extract relevant information from the payload
        reference = data["data"].get("reference")
        status = data["data"].get("status")
        if status == "success":
            # Find the pending payment in MongoDB using the reference ID
            print("here",reference)
            dustbin= mongo.db.dustbins.find_one({"reference_pay": reference})
            if dustbin:
                # Update Dustbin state to "paid"
                mongo.db.dustbins.update_one(
                    {"reference_pay": reference},
                    {"$set": {"reference_pay": "None"}}
                )
                # Remove the pending payment entry
                # mongo.db.pending_payments.delete_one({"reference": reference})

                # Notify the user of the successful payment using Redis Pub/Sub
                redis_client.publish(
                    dustbin["user_id"],
                    json.dumps({
                        "message": "Payment received. Trash will be picked up within 2 hours.",
                        "status": "success"
                    })
                )

    return jsonify({"status": "ok"}), 200


if __name__ == "__main__":
    app.run(debug=True)


"""
ON PAYMENT 
SEND R
REMOVE  PENDING 
RATHER ONLY SAY HOW FULL BIN IS AND NOTIFYING  CERTAIN LINK 
"""
