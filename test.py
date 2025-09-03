# turn this into a python script
# curl -X POST http://localhost:8080/server/add \
#   -H "Content-Type: application/json" \
#   -d '{
#     "name": "survival-1",
#     "ip": "192.168.1.100", 
#     "port": 25565,
#     "maxPlayers": 50
#   }'
  
import requests
import json

# url = "http://localhost:8080/server/add"
# headers = {"Content-Type": "application/json"}
# data = {
#     "name": "lobby",
#     "ip": "localhost",
#     "port": 30001,
#     "maxPlayers": 1
# }

# response = requests.post(url, headers=headers, data=json.dumps(data))




# same with this
# POST /server/enable
# Content-Type: application/json

# {
#   "name": "survival-1"
# }

url = "http://localhost:8080/server/test"
# data = {
#     "name": "lobby"
# }

response = requests.get(url)
print(response.status_code)
print(response.json())

with open('response_server.json', 'w') as f:
    json.dump(response.json(), f, indent=4)