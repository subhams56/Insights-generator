import pandas as pd

df = pd.read_csv("refined_network_data.csv")

carrier_mapping = {
    "Airtel": "AT&T",
    "Jio": "T-Mobile",
    "Vi": "Verizon",
    "BSNL": "US Cellular"
}

# Use correct column name
df["Carrier"] = df["Carrier"].replace(carrier_mapping)

df.to_csv("refined_network_metrics_US_carriers.csv", index=False)

print("✅ Done!")