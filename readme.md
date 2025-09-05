## :globe_with_meridians: **API Endpoints (HTTP)**

### **🏥 Health & Monitoring**
- `GET /health` - Health check endpoint
- `GET /performance/stats` - JVM performance metrics & stats
- `POST /performance/cache/clear` - Clear all controller caches

### **🖥️ Server Management**
- `GET /server/list` - Server list with status & player info
- `GET /server/overview` - Dashboard overview with summaries
- `GET /server/{name}` - Detailed info for specific server
- `POST /server/add` - Add new server to cluster
- `POST /server/remove` - Remove server from cluster
- `DELETE /server/remove` - Remove server from cluster (alternate)
- `POST /server/enable` - Enable a disabled server
- `POST /server/disable` - Disable an active server

### **📊 Metrics & Monitoring**
- `GET /metrics/{serverIp}` - Real-time server metrics
- `POST /metrics/{serverIp}` - Upload server metrics

### **🔄 Player Operations**
- `POST /transfer` - Transfer players between servers
- `GET /queue/list` - Get queued player list (UUID/username)
- `GET /queue/count` - Get current queue count
- `POST /queue/remove` - Remove player from queue

### **📢 Broadcasting**
- `POST /broadcast` - Send messages to all players

### **📝 Logging & Audit**
- `GET /cluster/logs` - Fetch recent cluster events
- `GET /cluster/logs/stats` - Get log statistics
- `DELETE /cluster/logs` - Clear all logs (admin only)

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
- **HTTP REST API** connection between Velocity proxy and each Minecraft server
- **Plugin responsibilities**:
  - Player transfer between servers
  - Health status reporting
  - Real-time metrics collection (TPS, CPU, RAM)
  - Server performance monitoring
  - Load balancing coordination

## :control_knobs: **Frontend Features**
- **Dashboard**: Server grid with real-time status and metrics
- **Server Details**: Live metrics, server info, performance graphs
- **Load Balancer**: Visual traffic distribution across servers
- **Monitoring**: Real-time charts for TPS, CPU, RAM, player counts
- **Alerts**: Real-time notifications for server issues
- **Performance Analytics**: Historical data and trends

## :zap: **Performance Features**
- **Smart caching with TTL** for all endpoints
- **Rate limiting** on management operations
- **Async processing** for better responsiveness
- **Dedicated thread pools** for ping operations
- **Automatic cache cleanup** every 5 minutes

## :gear: **Performance Configuration**
- **Server Status Cache**: 10s TTL
- **Individual Ping Cache**: 5s TTL
- **Queue Data Cache**: 2s TTL
- **Metrics Cache**: 3s TTL
- **Rate Limiting**: 1s minimum interval
- **Ping Timeout**: 3s maximum
- **Server Ping Pool**: 8 threads
- **Management Pool**: 4 threads

## :rocket: **Expected Performance Improvements**
- **40-70% faster** API response times
- **50-60% reduction** in network traffic
- **80-90% reduction** in database queries
- **20-30% reduction** in memory usage
- **Improved UI responsiveness** with real-time updates

## :chart_with_upwards_trend: **Scaling Strategy**
- **Horizontal**: Multiple Velocity instances behind load balancer
- **Database**: Migrate to PostgreSQL for production
- **Caching**: Redis for frequently accessed data
- **Monitoring**: Prometheus + Grafana for metrics

## :lock: **Security**
- **CORS**: Configured for frontend domain only
- **Plugin**: Shared secret for server communication
- **Network**: Internal network isolation for server communication

## :rocket: **Development Workflow**
1. **Start Velocity Proxy**: `java -jar velocity.jar` (port 8080 for API)
2. **Start Frontend**: `trunk serve` (port 8000)
3. **Start Spigot Servers**: With mc-kube plugin installed
4. **Test API**: Browser dev tools → Network for HTTP endpoints
5. **Monitor Metrics**: Real-time data flows every 2 seconds

## :zap: **Simplified Benefits**
- **No login complexity** - Direct access to dashboard
- **Faster development** - No auth middleware to maintain
- **Easier deployment** - No user management database
- **Perfect for personal/small team use** - Focus on server management features
- **Real-time monitoring** - Live metrics and performance data
- **Intelligent caching** - Optimized for high-performance server management

This architecture gives you **immediate actions via HTTP** and **live monitoring via real-time metrics** - perfect for managing distributed Minecraft servers with enterprise-level performance! :video_game: