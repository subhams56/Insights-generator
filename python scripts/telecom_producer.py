import json
import random
import time
from datetime import datetime, timedelta

from kafka import KafkaProducer

# =========================================================
# Kafka Producer Setup
# =========================================================

producer = KafkaProducer(
    bootstrap_servers='localhost:29092',
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

TOPIC = "telecom-network-metrics"

# =========================================================
# Static Telecom Metadata
# =========================================================

states = {
    "CA": ["Los Angeles", "San Diego", "San Francisco"],
    "TX": ["Dallas", "Houston", "Austin"],
    "NY": ["New York", "Buffalo", "Albany"],
    "FL": ["Miami", "Orlando", "Tampa"]
}

network_bands = [
    "5G mmWave",
    "5G Sub-6",
    "4G LTE"
]

environment_types = [
    "Urban",
    "Suburban",
    "Rural"
]

weather_conditions = [
    "Clear",
    "Rain",
    "Fog",
    "Storm"
]

device_models = [
    "iPhone 14",
    "Galaxy S23",
    "Pixel 8",
    "Nord 4"
]

carriers = [
    "Verizon",
    "AT&T",
    "T-Mobile"
]

congestion_levels = [
    "LOW",
    "MEDIUM",
    "HIGH"
]

# =========================================================
# Generate Realistic Telecom Record
# =========================================================

def generate_record():

    # -------------------------------------------
    # Time
    # -------------------------------------------

    now = datetime.now()

    hour = now.hour

    is_peak_hour = 1 if hour in [18, 19, 20, 21] else 0

    # -------------------------------------------
    # Geography
    # -------------------------------------------

    state = random.choice(list(states.keys()))
    city = random.choice(states[state])

    # -------------------------------------------
    # Telecom Metadata
    # -------------------------------------------

    band = random.choice(network_bands)
    env = random.choice(environment_types)
    weather = random.choice(weather_conditions)

    device = random.choice(device_models)
    carrier = random.choice(carriers)

    # -------------------------------------------
    # Base Metrics
    # -------------------------------------------

    latency = random.randint(15, 50)
    download = random.randint(80, 350)
    upload = random.randint(15, 80)

    packet_loss = round(random.uniform(0.1, 2.0), 2)

    active_users = random.randint(500, 5000)

    utilization = random.randint(30, 75)

    dropped_calls = random.randint(0, 5)

    # -------------------------------------------
    # Peak Hour Impact
    # -------------------------------------------

    if is_peak_hour:
        active_users += random.randint(2000, 6000)
        utilization += random.randint(10, 25)

        latency += random.randint(10, 35)

    # -------------------------------------------
    # Weather Impact on mmWave
    # -------------------------------------------

    if weather in ["Rain", "Storm"] and band == "5G mmWave":

        latency += random.randint(40, 90)

        packet_loss += round(random.uniform(2.0, 8.0), 2)

        dropped_calls += random.randint(5, 15)

        utilization += random.randint(5, 15)

    # -------------------------------------------
    # Device-specific anomaly
    # -------------------------------------------

    if device == "Nord 4":

        dropped_calls += random.randint(1, 6)

        download -= random.randint(20, 80)

    # -------------------------------------------
    # Congestion Classification
    # -------------------------------------------

    if utilization < 50:
        congestion = "LOW"
    elif utilization < 80:
        congestion = "MEDIUM"
    else:
        congestion = "HIGH"

    # -------------------------------------------
    # Quality Score
    # -------------------------------------------

    quality_score = round(
        max(
            0.1,
            min(
                1.0,
                (
                    (download / 350)
                    - (packet_loss / 10)
                    - (latency / 300)
                )
            )
        ),
        2
    )

    # -------------------------------------------
    # Final JSON Payload
    # -------------------------------------------

    record = {
        "timestamp": now.isoformat(),
        "hour_of_day": hour,
        "is_peak_hour": is_peak_hour,
        "region": "US",
        "state": state,
        "city": city,
        "network_band": band,
        "environment_type": env,
        "avg_latency_ms": latency,
        "download_speed_mbps": max(download, 5),
        "upload_speed_mbps": upload,
        "packet_loss_pct": packet_loss,
        "active_users": active_users,
        "network_utilization_pct": utilization,
        "congestion_level": congestion,
        "dropped_calls": dropped_calls,
        "weather_condition": weather,
        "quality_score": quality_score,
        "device_model": device,
        "carrier": carrier
    }

    return record

# =========================================================
# Continuous Streaming Loop
# =========================================================

print("Starting Telecom Telemetry Producer...")

while True:

    payload = generate_record()

    producer.send(TOPIC, payload)

    print(f"Produced: {payload}")

    time.sleep(5)