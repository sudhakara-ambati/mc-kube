import requests
import json

# # GET the metrics
# print("=== FETCHING METRICS ===")
# get_url = "http://localhost:8080/metrics/26.177.172.5"

# try:
#     get_response = requests.get(get_url)
#     print(f"Status Code: {get_response.status_code}")
#     print(f"Headers: {dict(get_response.headers)}")
    
#     if get_response.status_code == 200:
#         try:
#             response_json = get_response.json()
#             print("Response JSON:")
#             print(json.dumps(response_json, indent=2))
#         except requests.exceptions.JSONDecodeError:
#             print("Response (not JSON):")
#             print(get_response.text if get_response.text else "[Empty response]")
#     else:
#         print(f"Error response: {get_response.text}")
        
# except requests.exceptions.RequestException as e:
#     print(f"Request failed: {e}")

log_url = "http://localhost:8080/cluster/logs/"
def fetch_logs():
    print("\n=== LOGS ===")
    try:
        log_response = requests.get(log_url)
        print(f"Status Code: {log_response.status_code}")
        print(f"Headers: {dict(log_response.headers)}")

        if log_response.status_code == 200:
            try:
                response_json = log_response.json()
                print("Response JSON:")
                print(json.dumps(response_json, indent=2))
                with open("logs.json", "w") as f:
                    json.dump(response_json, f, indent=2)
            except requests.exceptions.JSONDecodeError:
                print("Response (not JSON):")
                print(log_response.text if log_response.text else "[Empty response]")
        else:
            print(f"Error response: {log_response.text}")

    except requests.exceptions.RequestException as e:
        print(f"Request failed: {e}")
fetch_logs()