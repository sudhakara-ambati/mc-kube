import requests, json
import time

response = requests.get(f"http://localhost:8080/queue/list")
data = response.json()
print(data) # Wait for 5 seconds before the next request

response = requests.get(f"http://localhost:8080/queue/list",
                        params = {"type": "uuids"})
data = response.json()
print(data) # Wait for 5 seconds before the next request

response = requests.get(f"http://localhost:8080/queue/count")
data = response.json()
print(data) # Wait for 5 seconds before the next request

# def broadcast(message):
#     response = requests.post(
#         "http://localhost:8080/broadcast/",
#         json={"message": message}
#     )
#     print(f"Status: {response.status_code}")
#     print(f"Response: {response.json()}")

# broadcast("nath nath nath nath naaaaaath")
def metrics():
    response = requests.get("http://localhost:8080/metrics/queue")
    print(f"Status: {response.status_code}")
    print(f"Response: {response.json()}")
    
# write a command to request /cluster/logs and then write it into a json file with indent 2
def logs():
    response = requests.get("http://localhost:8080/cluster/logs")
    print(f"Status: {response.status_code}")
    with open("logs.json", "w") as f:
        json.dump(response.json(), f, indent=2)
        
logs()


# inp = input("Kick? (y/n): ")

# if inp.lower() == "y":
#     player = input("Enter player name: ")
#     response = requests.post(
#         "http://localhost:8080/queue/remove",
#         json={"player": player, "type": "username"}
#     )

#     print(f"Status: {response.status_code}")
#     print(f"Response: {response.json()}")

# ip = '26.40.23.207'
# local_ip = '127.0.0.1'
# req = requests.get(f"http://localhost:8080/metrics/{ip}")
# # get the process_cpu_percent and system_cpu_percent values from json
# data = req.json()
# print(data)
# print(f"Process CPU Percent: {data['process_cpu_percent']}")
# print(f"System CPU Percent: {data['system_cpu_percent']}")

import requests
req = requests.get("http://localhost:8080/server/list")
data = req.json()
print(data)