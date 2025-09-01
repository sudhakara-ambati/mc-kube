
## :globe_with_meridians: **API Endpoints (HTTP)**

### **Server Management**
- `GET /api/servers` - List all servers
- `POST /api/servers` - Create new server
- `GET /api/servers/{id}` - Get server details
- `PUT /api/servers/{id}` - Update server config
- `DELETE /api/servers/{id}` - Remove server

### **Server Control**
- `POST /api/servers/{id}/start` - Start server
- `POST /api/servers/{id}/stop` - Stop server
- `POST /api/servers/{id}/restart` - Restart server

### **Configuration**
- `GET /api/servers/{id}/config` - Get server config
- `PUT /api/servers/{id}/config` - Update server config

## :bar_chart: **WebSocket Events**

### **Server → Client (Broadcasts)**
- `server_update` - Status changes (online/offline/error)
- `player_count_response` - Live player data
- `server_logs_response` - Streaming logs
- `metrics_update` - Performance data
- `alert` - Error/warning notifications

### **Client → Server (Requests)**
- `get_player_count` - Request current players
- `get_server_logs` - Request recent logs
- `subscribe` - Subscribe to server updates
- `execute_command` - Run command with live output

## :electric_plug: **Java Plugin Integration**
- **TCP/WebSocket connection** between Rust and each Minecraft server
- **Plugin responsibilities**:
  - Player transfer between servers
  - Health status reporting
  - Command execution
  - Log streaming
  - Performance metrics collection

## :control_knobs: **Frontend Features**
- **Dashboard**: Server grid with real-time status
- **Server Details**: Logs, players, config in real-time
- **Load Balancer**: Visual traffic distribution
- **Monitoring**: Charts for CPU, RAM, player counts
- **Alerts**: Real-time notifications for issues
- **Command Console**: Execute commands with live output

## :chart_with_upwards_trend: **Scaling Strategy**
- **Horizontal**: Multiple Rust instances behind load balancer
- **Database**: Migrate to PostgreSQL for production
- **Caching**: Redis for frequently accessed data
- **Monitoring**: Prometheus + Grafana for metrics

## :lock: **Security**
- **CORS**: Configured for frontend domain only
- **Plugin**: Shared secret for server communication
- **Network**: Internal network isolation for server communication

## :rocket: **Development Workflow**
1. **Start Backend**: `cargo run` (port 8080)
2. **Start Frontend**: `npm run dev` (port 3000)
3. **Test WebSocket**: Browser dev tools → Network → WS
4. **API Testing**: Postman/curl for HTTP endpoints

## :zap: **Simplified Benefits**
- **No login complexity** - Direct access to dashboard
- **Faster development** - No auth middleware to maintain
- **Easier deployment** - No user management database
- **Perfect for personal/small team use** - Focus on server management features

This architecture gives you **immediate actions via HTTP** and **live monitoring via WebSockets** - perfect for managing distributed Minecraft servers without enterprise overhead! :video_game: