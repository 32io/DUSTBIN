# Use an official Python runtime as a parent image
FROM python:3.11-slim

# Set the working directory in the container

# Copy the requirements file to the working directory
COPY . .

# Install any needed packages specified in requirements.txt
RUN pip install --no-cache-dir -r req.txt

# # Copy the rest of the application code to the working directory
# COPY . .

# Expose the port that the Flask app runs on
EXPOSE 5000

# Define environment variables
ENV FLASK_APP=app.py
ENV FLASK_RUN_HOST=0.0.0.0

# Command to run the application
CMD ["flask", "run"]
