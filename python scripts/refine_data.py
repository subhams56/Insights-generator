import pandas as pd
import numpy as np

# 1. Load CSV
df = pd.read_csv("5g_network_data.csv")

# 2. Rename columns (map your dataset → expected schema)
df = df.rename(columns={
    "Timestamp": "timestamp",
    "Location": "city",
    "Download Speed (Mbps)": "download_speed_mbps",
    "Upload Speed (Mbps)": "upload_speed_mbps",
    "Latency (ms)": "avg_latency_ms",
    "Band": "network_band",
    "Network Congestion Level": "congestion_level",
    "Dropped Connection": "dropped_calls"
})

# 3. Convert dropped_calls from boolean → integer (0/1 → realistic counts)
df["dropped_calls"] = df["dropped_calls"].astype(int)
df["dropped_calls"] = df["dropped_calls"].apply(
    lambda x: np.random.randint(1, 5) if x == 1 else 0
)

# 4. Convert timestamp + extract hour
df["timestamp"] = pd.to_datetime(df["timestamp"])
df["hour_of_day"] = df["timestamp"].dt.hour

# ============================================================
# 🟢 US GEOGRAPHY OVERRIDE (THIS IS THE MAIN CHANGE)
# ============================================================

us_locations = [
    {"city": "New York City", "state": "NY", "region": "Northeast"},
    {"city": "Albany", "state": "NY", "region": "Northeast"},
    {"city": "Austin", "state": "TX", "region": "South"},
    {"city": "Dallas", "state": "TX", "region": "South"},
    {"city": "San Francisco", "state": "CA", "region": "West"},
    {"city": "Los Angeles", "state": "CA", "region": "West"},
    {"city": "Chicago", "state": "IL", "region": "Midwest"},
    {"city": "Springfield", "state": "IL", "region": "Midwest"},
    {"city": "Seattle", "state": "WA", "region": "West"},
    {"city": "Miami", "state": "FL", "region": "South"}
]

# Assign US geography randomly
geo_df = pd.DataFrame(
    [us_locations[np.random.randint(0, len(us_locations))] for _ in range(len(df))]
)

df["city"] = geo_df["city"]
df["state"] = geo_df["state"]
df["region"] = geo_df["region"]

# ============================================================
# 5. Environment type (based on city realism)
# ============================================================

df["environment_type"] = np.where(
    df["city"].isin(["New York City", "Chicago", "San Francisco", "Los Angeles"]),
    "Urban",
    np.where(
        df["city"].isin(["Albany", "Springfield"]),
        "Suburban",
        "Rural"
    )
)

# ============================================================
# 6. Active users (based on congestion + environment)
# ============================================================

df["active_users"] = np.where(
    df["congestion_level"] == "High",
    np.random.randint(800, 1500, len(df)),
    np.random.randint(100, 800, len(df))
)

# ============================================================
# 7. Packet loss (derived)
# ============================================================

df["packet_loss_pct"] = np.round(
    df["avg_latency_ms"] * np.random.uniform(0.01, 0.05, len(df)), 2
)

# ============================================================
# 8. Weather
# ============================================================

df["weather_condition"] = np.random.choice(
    ["Clear", "Rain", "Storm", "Snow"],
    p=[0.7, 0.15, 0.1, 0.05],
    size=len(df)
)

# ============================================================
# 9. Network utilization
# ============================================================

df["network_utilization_pct"] = np.round(
    (df["active_users"] / df["active_users"].max()) * 100, 2
)

# ============================================================
# 10. Quality score
# ============================================================

df["quality_score"] = np.round(
    (df["download_speed_mbps"] / df["download_speed_mbps"].max()) * 0.5 +
    (1 - df["packet_loss_pct"] / df["packet_loss_pct"].max()) * 0.3 +
    (1 - df["avg_latency_ms"] / df["avg_latency_ms"].max()) * 0.2,
    2
)

# ============================================================
# 11. Peak hour flag
# ============================================================

df["is_peak_hour"] = df["hour_of_day"].apply(
    lambda x: 1 if 18 <= x <= 23 else 0
)

# ============================================================
# 12. Anomalies
# ============================================================

failure_mask = np.random.rand(len(df)) < 0.02

df.loc[failure_mask, "download_speed_mbps"] *= 0.2
df.loc[failure_mask, "avg_latency_ms"] *= 2
df.loc[failure_mask, "packet_loss_pct"] *= 3
df.loc[failure_mask, "dropped_calls"] *= 2

# ============================================================
# 13. Save
# ============================================================

df.to_csv("refined_network_data.csv", index=False)

print("✅ US-centric refined CSV generated!")