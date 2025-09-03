use yew::{function_component, html, Html, use_state, use_effect_with, Callback};
use yew_router::prelude::*;
use gloo_console;
use serde::Deserialize;
use serde_json;
use crate::Route;
use crate::utils::{api_get};
use gloo_timers::callback::Interval;
use chrono::{DateTime, Utc};

#[derive(Debug, Clone, Deserialize, Default)]
pub struct ServerDetail {
    #[serde(rename = "maxPlayers")]
    pub max_players: i32,
    #[serde(rename = "loadStatus")]
    pub load_status: String,
    #[serde(rename = "loadPercentage")]
    pub load_percentage: f64,
    pub name: String,
    #[serde(rename = "currentPlayers")]
    pub current_players: i32,
    #[serde(default = "default_status")]
    pub status: String,
    #[serde(default = "default_enabled")]
    pub enabled: bool,
    #[serde(rename = "isHealthy", default = "default_healthy")]
    pub is_healthy: bool,
}

fn default_status() -> String {
    "unknown".to_string()
}

fn default_enabled() -> bool {
    true
}

fn default_healthy() -> bool {
    false
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct ServerOverview {
    #[serde(rename = "totalPlayers")]
    pub total_players: i32,
    #[serde(rename = "totalMaxPlayers")]
    pub total_max_players: i32,
    #[serde(rename = "disabledServers")]
    pub disabled_servers: i32,
    #[serde(rename = "enabledServers")]
    pub enabled_servers: i32,
    #[serde(rename = "healthyServers")]
    pub healthy_servers: i32,
    #[serde(rename = "onlineServers")]
    pub online_servers: i32,
    #[serde(rename = "offlineServers")]
    pub offline_servers: i32,
    #[serde(rename = "totalServers")]
    pub total_servers: i32,
    pub message: String,
    pub success: bool,
    #[serde(rename = "networkLoadPercentage")]
    pub network_load_percentage: f64,
    pub timestamp: String,
    #[serde(rename = "playerDetails")]
    pub server_details: Vec<ServerDetail>,
}

fn format_timestamp(ts: &str) -> String {
    match DateTime::parse_from_rfc3339(ts) {
        Ok(dt) => dt.with_timezone(&Utc).format("%Y-%m-%d %H:%M:%S UTC").to_string(),
        Err(_) => ts.to_string(),
    }
}

#[function_component(Dashboard)]
pub fn dashboard() -> Html {
    let overview = use_state(|| ServerOverview::default());
    let navigator = use_navigator().unwrap();

{
    let overview = overview.clone();
    use_effect_with((), move |_| {
        let overview = overview.clone();
        
        api_get("http://localhost:8080/server/overview", {
            let overview = overview.clone();
            Callback::from(move |data: String| {
                gloo_console::log!("Raw overview response:", &data);
                
                match serde_json::from_str::<ServerOverview>(&data) {
                    Ok(parsed) => {
                        gloo_console::log!("Successfully parsed overview!");
                        gloo_console::log!("Server details count:", &parsed.server_details.len().to_string());
                        gloo_console::log!("Total players:", &parsed.total_players.to_string());
                        overview.set(parsed);
                    },
                    Err(e) => {
                        gloo_console::log!("Failed to parse overview data - error:", &e.to_string());
                        gloo_console::log!("Raw data was:", &data);
                        
                        if let Ok(json_value) = serde_json::from_str::<serde_json::Value>(&data) {
                            if let Some(player_details) = json_value.get("playerDetails") {
                                gloo_console::log!("PlayerDetails found:", &player_details.to_string());
                                
                                match serde_json::from_str::<Vec<ServerDetail>>(&player_details.to_string()) {
                                    Ok(details) => gloo_console::log!("ServerDetail parsing works, count:", &details.len().to_string()),
                                    Err(detail_err) => gloo_console::log!("ServerDetail parsing failed:", &detail_err.to_string()),
                                }
                            }
                        }
                    }
                }
            })
        });
        
        let interval = Interval::new(15000, move || {
            gloo_console::log!("Auto-refreshing dashboard data...");
            api_get("http://localhost:8080/server/overview", {
                let overview = overview.clone();
                Callback::from(move |data: String| {
                    if let Ok(parsed) = serde_json::from_str::<ServerOverview>(&data) {
                        overview.set(parsed);
                    }
                })
            });
        });
        move || drop(interval)
    });
}

    html! {
        <div class="dashboard-page">
            <div class="dashboard-grid">
                <div class="card metrics-card">
                    <div class="card-header">
                        <h2>{"Overview"}</h2>
                        <span class="auto-refresh">{"● Auto-refresh"}</span>
                    </div>
                    <div class="card-content">
                        <div class="metrics-display">
                            <div class="metric-row">
                                <span class="metric-label">{"Total Players:"}</span>
                                <span class="metric-value">{overview.total_players}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Max Players:"}</span>
                                <span class="metric-value">{overview.total_max_players}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Online Servers:"}</span>
                                <span class="metric-value status-online">{overview.online_servers}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Offline Servers:"}</span>
                                <span class="metric-value status-offline">{overview.offline_servers}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Healthy Servers:"}</span>
                                <span class="metric-value status-healthy">{overview.healthy_servers}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Disabled Servers:"}</span>
                                <span class="metric-value status-disabled">{overview.disabled_servers}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Enabled Servers:"}</span>
                                <span class="metric-value">{overview.enabled_servers}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Total Servers:"}</span>
                                <span class="metric-value">{overview.total_servers}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Network Load:"}</span>
                                <span class="metric-value network-load">{format!("{:.1}%", overview.network_load_percentage)}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Status:"}</span>
                                <span class="metric-value success-status">{if overview.success { "✓ Online" } else { "✗ Error" }}</span>
                            </div>
                            <div class="metric-row metric-message">
                                <span class="metric-label">{"Message:"}</span>
                                <span class="metric-value">{&overview.message}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Last Update:"}</span>
                                <span class="metric-value timestamp">{format_timestamp(&overview.timestamp)}</span>
                            </div>
                        </div>
                    </div>
                </div>
                    <div class="card server-table-card">
                        <div class="card-header">
                            <h2>{"All Servers"}</h2>
                        </div>
                        <div class="card-content">
                            <table class="server-table">
                                <thead>
                                    <tr>
                                        <th>{"Name"}</th>
                                        <th>{"Status"}</th>
                                        <th>{"Current Players"}</th>
                                        <th>{"Max Players"}</th>
                                        <th>{"Load Status"}</th>
                                        <th>{"Load %"}</th>
                                        <th>{"Health"}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {for overview.server_details.iter().map(|server| {
                                        let load_class = match server.load_status.as_str() {
                                            "low" => "load-low",
                                            "medium" => "load-medium", 
                                            "high" => "load-high",
                                            "offline" => "load-offline",
                                            _ => "",
                                        };
                                        let status_class = match server.status.as_str() {
                                            "online" => "status-online",
                                            "offline" => "status-offline", 
                                            "disabled" => "status-disabled",
                                            _ => "status-unknown",
                                        };
                                        let health_class = if server.is_healthy { "status-healthy" } else { "status-unhealthy" };
                                        let name = server.name.clone();
                                        let navigator = navigator.clone();
                                        html! {
                                            <tr onclick={Callback::from(move |_| navigator.push(&Route::ServerDetail { id: name.clone() }))}>
                                                <td>{&server.name}</td>
                                                <td>
                                                    <span class={format!("server-status {}", status_class)}>
                                                        {if !server.enabled { "Disabled" } else if server.status == "unknown" { "Unknown" } else { &server.status }}
                                                    </span>
                                                </td>
                                                <td>{server.current_players}</td>
                                                <td>{if server.max_players == -1 { "∞".to_string() } else { server.max_players.to_string() }}</td>
                                                <td>
                                                    <span class={format!("load-status {}", load_class)}>
                                                        {&server.load_status}
                                                    </span>
                                                </td>
                                                <td>{format!("{:.1}%", server.load_percentage)}</td>
                                                <td>
                                                    <span class={format!("health-status {}", health_class)}>
                                                        {if server.is_healthy { "✓ Healthy" } else { "✗ Unhealthy" }}
                                                    </span>
                                                </td>
                                            </tr>
                                        }
                                    })}
                                </tbody>
                            </table>
                        </div>
                    </div>
            </div>
        </div>
    }
}