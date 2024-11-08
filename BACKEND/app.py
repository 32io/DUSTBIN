import requests
from flask import Flask, request, jsonify, session, Response, stream_with_context
from pymongo import MongoClient
from werkzeug.security import generate_password_hash, check_password_hash
from flask_sse import sse
from bson.json_util import dumps

# import json
app = Flask(__name__)
app.secret_key = "your_secret_key"  # Change to a more secure secret
app.config["REDIS_URL"] = "redis://localhost"  # for SSE
app.register_blueprint(sse, url_prefix="/stream")

# Initialize MongoDB
mongo = MongoClient("mongodb://localhost:27017/trash_mgmt")
mongo.db.dustbins.create_index([("email", 1)], unique=True)
# 3rd Party Payment (Paystack Mobile Money Integration)
PAYSTACK_SECRET_KEY = (
    "your_paystack_secret_key"  # Replace with your actual Paystack secret key
)


# Function to call Paystack's Mobile Money API
def initiate_payment(amount, email, phone, provider):
    url = "https://api.paystack.co/charge"
    headers = {
        "Authorization": f"Bearer {PAYSTACK_SECRET_KEY}",
        "Content-Type": "application/json",
    }
    payload = {
        "amount": amount,  # The amount in the smallest currency unit (e.g., GHS)
        "email": email,  # The user's email
        "currency": "GHS",  # Currency, GHS for Ghana Cedi
        "mobile_money": {
            "phone": phone,  # The phone number
            "provider": provider,  # The mobile money provider (MTN, Vodafone, AirtelTigo)
        },
    }
    response = requests.post(url, json=payload, headers=headers)

    if response.status_code == 200:
        return response.json()
    else:
        return {"error": "Payment initiation failed", "details": response.json()}


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
    print(dustbins)
    return Response(dumps(dustbins), mimetype="application/json")


# 6. Register Dustbin
@app.route("/register_dustbin", methods=["POST"])
def register_dustbin():
    data = request.json
    mongo.db.dustbins.update_one(
        {"dustbin_id": data["dustbin_id"]},
        {"$set": {"state": data.get("state", "empty")}},
        upsert=True,
    )
    return jsonify({"message": "Dustbin registered successfully"}), 200


# 7. Update Dustbin State
@app.route("/dustbin_state", methods=["POST"])
def update_dustbin_state():
    data = request.json
    dustbin_id = data.get("dustbin_id")
    state = data.get("state")

    mongo.db.dustbins.update_one(
        {"dustbin_id": dustbin_id},
        {"$set": {"state": state}},
        # upsert=True,
    )
    dustbin = mongo.db.dustbins.find_one({"dustbin_id": dustbin_id})
    print(dustbin)
    if dustbin.get("user_id"):

        # return jsonify({"error": "Dustbin  user not found"}), 404
        with app.app_context():
            sse.publish(
                {"message": "trash", "data": data},
                type="trash_state",
                channel=str(dustbin["user_id"]),
            )
    # When the dustbin is full, initiate payment process
    if state == "full":
        print("here")
        dustbin = mongo.db.dustbins.find_one({"dustbin_id": dustbin_id})
        if not str(dustbin.get("user_id")):
            return jsonify({"error": "Dustbin  user not found"}), 404
        user_id = dustbin["user_id"]
        user_email = "customer@email.com"  # Replace with the user's email retrieved from the database
        phone_number = "+254701860614"  # Replace with the user's phone number
        provider = "mpesa"  # Replace with the correct mobile money provider

        payment_response = initiate_payment(100, user_email, phone_number, provider)

        if "error" not in payment_response:
            pay_url = payment_response.get("data", {}).get("authorization_url", "")
            mongo.db.pending_payments.insert_one(
                {"user_id": user_id, "dustbin_id": dustbin_id, "payment_url": pay_url}
            )
            sse.publish(
                {"message": "Payment pending", "payment_url": pay_url},
                type="payment_pending",
                channel=user_id,
            )
        else:
            return (
                jsonify(
                    {"error": "Payment initiation failed", "details": payment_response}
                ),
                500,
            )

    return jsonify({"message": "Dustbin state updated"}), 200


# 8. Get Payment Link
@app.route("/get_payment_link", methods=["GET"])
def get_payment_link():
    user_id = session.get("user_id")
    pending = mongo.db.pending_payments.find_one({"user_id": user_id})
    if pending:
        return jsonify({"payment_url": pending["payment_url"]}), 200
    return jsonify({"message": "No pending payment"}), 404


# 9. Payment Webhook (Paystack Callback)
@app.route("/payment_webhook", methods=["POST"])
def payment_webhook():
    data = request.json
    if data["event"] == "charge.success":
        reference = data["data"]["reference"]
        pending_payment = mongo.db.pending_payments.find_one({"reference": reference})

        if pending_payment:
            # Update Dustbin Status to Paid
            mongo.db.dustbins.update_one(
                {"dustbin_id": pending_payment["dustbin_id"]},
                {"$set": {"state": "paid"}},
            )
            mongo.db.pending_payments.delete_one({"reference": reference})
            # Notify User of Payment Success
            sse.publish(
                {"message": "Payment received. Trash will be picked in 2 hours."},
                type="payment_received",
                channel=pending_payment["user_id"],
            )

    return jsonify({"status": "ok"}), 200


# 10. SSE Notification Stream (Real-Time)
@app.route("/notifications")
@stream_with_context
def notifications():
    user_id = session.get("user_id")
    print(user_id)
    if not user_id:
        return jsonify({"error": "Unauthorized"}), 403

    return sse.messages(channel=user_id)


if __name__ == "__main__":
    app.run(debug=True)
