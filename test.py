import requests
import json

# GET the metrics
print("=== FETCHING METRICS ===")
get_url = "http://localhost:8080/metrics/26.177.172.5"

try:
    get_response = requests.get(get_url)
    print(f"Status Code: {get_response.status_code}")
    print(f"Headers: {dict(get_response.headers)}")
    
    if get_response.status_code == 200:
        try:
            response_json = get_response.json()
            print("Response JSON:")
            print(json.dumps(response_json, indent=2))
        except requests.exceptions.JSONDecodeError:
            print("Response (not JSON):")
            print(get_response.text if get_response.text else "[Empty response]")
    else:
        print(f"Error response: {get_response.text}")
        
except requests.exceptions.RequestException as e:
    print(f"Request failed: {e}")