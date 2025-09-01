curl -X POST http://localhost:8080/server/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "survival-1",
    "ip": "192.168.1.100", 
    "port": 25565,
    "maxPlayers": 50
  }'