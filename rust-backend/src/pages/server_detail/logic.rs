use yew::{function_component, html, Html, use_state, use_effect_with, Callback, MouseEvent, Properties};
use yew_router::prelude::*;
use crate::utils::{api_post, ApiError};
use crate::Route;
use wasm_bindgen_futures::spawn_local;
use gloo_net::http::Request;
use serde::Deserialize;
use gloo_console::log;
use gloo_timers::callback::Interval;

#[derive(Properties, PartialEq)]
pub struct ServerDetailProps {
    pub server_id: String,
}

#[derive(Clone, PartialEq, Deserialize, Default)]
pub struct ServerMetrics {
    #[serde(rename = "server_ip", default)]
    pub server_ip: String,
    #[serde(rename = "system_cpu_percent", default)]
    pub system_cpu_percent: f64,
    #[serde(rename = "process_cpu_percent", default)]
    pub process_cpu_percent: f64,
    #[serde(rename = "memory_used_gb", default)]
    pub memory_used_gb: f64,
    #[serde(rename = "memory_max_gb", default)]
    pub memory_max_gb: f64,
    #[serde(rename = "memory_percent", default)]
    pub memory_percent: f64,
    #[serde(rename = "system_memory_used_gb", default)]
    pub system_memory_used_gb: f64,
    #[serde(rename = "system_memory_total_gb", default)]
    pub system_memory_total_gb: f64,
    #[serde(rename = "system_memory_percent", default)]
    pub system_memory_percent: f64,
    #[serde(default)]
    pub tps: f64,
    #[serde(rename = "tps_percent", default)]
    pub tps_percent: f64,
    #[serde(default)]
    pub success: bool,
    #[serde(default)]
    pub timestamp: String,
}

#[derive(Clone, PartialEq, Deserialize, Default)]
pub struct ServerDetailInfo {
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub host: String,
    #[serde(default)]
    pub port: i32,
    #[serde(default)]
    pub status: String,
    #[serde(default)]
    pub enabled: bool,
    #[serde(rename = "currentPlayers", default)]
    pub current_players: i32,
    #[serde(rename = "maxPlayers", default)]
    pub max_players: i32,
    #[serde(default)]
    pub latency: i32,
    #[serde(default)]
    pub version: String,
    #[serde(default)]
    pub motd: String,
    #[serde(rename = "loadPercentage", default)]
    pub load_percentage: f64,
    #[serde(rename = "loadStatus", default)]
    pub load_status: String,
    #[serde(rename = "isHealthy", default)]
    pub is_healthy: bool,
    #[serde(rename = "serverTimestamp", default)]
    pub server_timestamp: String,
}

#[function_component(ServerDetail)]
pub fn server_detail(props: &ServerDetailProps) -> Html {
    let server_info = use_state(ServerDetailInfo::default);
    let metrics = use_state(ServerMetrics::default);
    let response = use_state(|| String::new());
    let show_delete_modal = use_state(|| false);
    let navigator = use_navigator().unwrap();

    {
        let server_info = server_info.clone();
        let server_id = props.server_id.clone();
        use_effect_with(server_id, move |server_id| {
            let server_info = server_info.clone();
            let server_id = server_id.clone();
            spawn_local(async move {
                let request_url = format!("http://localhost:8080/server/{}", server_id);
                log!("Fetching server info from:", request_url.clone());
                
                match Request::get(&request_url).send().await {
                    Ok(resp) => {
                        log!("Server info response status:", resp.status().to_string());
                        match resp.json::<ServerDetailInfo>().await {
                            Ok(data) => {
                                log!("Successfully parsed server info for:", data.name.clone());
                                server_info.set(data);
                            }
                            Err(e) => log!("Failed to parse server info:", e.to_string()),
                        }
                    },
                    Err(e) => log!("Failed to fetch server info:", e.to_string()),
                }
            });
            || ()
        });
    }

    {
        let metrics = metrics.clone();
        use_effect_with(server_info.clone(), move |server_info| {
            let server_ip = server_info.host.clone();
            let is_ip_loaded = !server_ip.is_empty();

            log!("Setting up metrics fetching for server IP:", server_ip.clone());

            let interval = if is_ip_loaded {
                let fetch_metrics = move || {
                    let metrics = metrics.clone();
                    let server_ip = server_ip.clone();
                    spawn_local(async move {
                        let request_url = format!("http://localhost:8080/metrics/{}", server_ip);
                        log!("Fetching metrics from:", request_url.clone());
                        
                        match Request::get(&request_url).send().await {
                            Ok(resp) => {
                                log!("Metrics response status:", resp.status().to_string());
                                match resp.json::<ServerMetrics>().await {
                                    Ok(data) => {
                                        log!("Successfully parsed metrics data");
                                        metrics.set(data);
                                    },
                                    Err(e) => log!("Failed to parse metrics JSON:", e.to_string()),
                                }
                            },
                            Err(e) => log!("Failed to fetch metrics:", e.to_string()),
                        }
                    });
                };

                fetch_metrics();
                Some(Interval::new(2000, fetch_metrics))
            } else {
                log!("Server IP not loaded yet, skipping metrics fetch");
                None
            };

            move || {
                drop(interval);
            }
        });
    }

    let show_delete_confirmation = {
        let show_delete_modal = show_delete_modal.clone();
        Callback::from(move |_: MouseEvent| {
            show_delete_modal.set(true);
        })
    };

    let close_delete_modal = {
        let show_delete_modal = show_delete_modal.clone();
        Callback::from(move |_: MouseEvent| {
            show_delete_modal.set(false);
        })
    };

    let confirm_delete = {
        let response = response.clone();
        let server_id = props.server_id.clone();
        let show_delete_modal = show_delete_modal.clone();
        let navigator = navigator.clone();
        Callback::from(move |_: MouseEvent| {
            let server_data = format!(
                r#"{{"name":"{}"}}"#,
                server_id
            );

            api_post("http://127.0.0.1:8080/server/remove", Some(&server_data), {
                let response = response.clone();
                let show_delete_modal = show_delete_modal.clone();
                let navigator = navigator.clone();
                Callback::from(move |result: Result<String, ApiError>| {
                    log!("API Response received:", format!("{:?}", result));
                    match result {
                        Ok(data) => {
                            log!("Success response:", data.clone());
                            response.set(format!("Server deleted successfully: {}", data));
                            show_delete_modal.set(false);
                            navigator.push(&Route::Servers);
                        }
                        Err(e) => {
                            log!("Error response:", format!("{:?}", e));
                            log!("Error string:", e.to_string());
                            response.set(format!("Error deleting server: {}", e));
                            show_delete_modal.set(false);
                        }
                    }
                })
            });
        })
    };

html! {
    <div class="server-detail-page">
        <div class="page-header">
            <h1>{ format!("Server Details: {}", &server_info.name) }</h1>
            <div class="header-actions">
                <button class="btn btn-danger" onclick={show_delete_confirmation}>
                    <span class="btn-icon">{ "Delete Server" }</span>
                </button>
            </div>
        </div>

        <div class="detail-grid">
            <div class="card server-info-card">
                <div class="card-header">
                    <h2>{ "Server Information" }</h2>
                    <div class="server-status-badge">
                        <span class={format!("status-dot {}", server_info.status.to_lowercase())}></span>
                        <span class="status-text">{ &server_info.status }</span>
                    </div>
                </div>
                <div class="card-body">
                    <div class="server-info-grid">
                        <div class="info-card primary-info">
                            <div class="info-icon">{"SRV"}</div>
                            <div class="info-content">
                                <div class="info-label">{"Server Name"}</div>
                                <div class="info-value primary">{ &server_info.name }</div>
                            </div>
                        </div>

                        <div class="info-card network-info">
                            <div class="info-icon">{"NET"}</div>
                            <div class="info-content">
                                <div class="info-label">{"Network Address"}</div>
                                <div class="info-value">{ format!("{}:{}", &server_info.host, server_info.port) }</div>
                            </div>
                        </div>

                        <div class="info-card players-info">
                            <div class="info-icon">{"USR"}</div>
                            <div class="info-content">
                                <div class="info-label">{"Players Online"}</div>
                                <div class="info-value">{ format!("{} / {}", server_info.current_players, 
                                    if server_info.max_players == -1 { "∞".to_string() } else { server_info.max_players.to_string() }
                                ) }</div>
                                <div class="player-progress-bar">
                                    <div class="player-progress-fill" style={format!("width: {}%", 
                                        if server_info.max_players > 0 { 
                                            (server_info.current_players as f64 / server_info.max_players as f64 * 100.0).min(100.0)
                                        } else { 0.0 }
                                    )}></div>
                                </div>
                            </div>
                        </div>

                        <div class="info-card version-info">
                            <div class="info-icon">{"VER"}</div>
                            <div class="info-content">
                                <div class="info-label">{"Version"}</div>
                                <div class="info-value">{ &server_info.version }</div>
                            </div>
                        </div>

                        <div class="info-card performance-info">
                            <div class="info-icon">{"LAT"}</div>
                            <div class="info-content">
                                <div class="info-label">{"Latency"}</div>
                                <div class="info-value">{ format!("{}ms", server_info.latency) }</div>
                                <div class={format!("latency-indicator {}", 
                                    if server_info.latency < 50 { "good" } 
                                    else if server_info.latency < 100 { "okay" } 
                                    else { "poor" }
                                )}>
                                    {if server_info.latency < 50 { "Excellent" } 
                                     else if server_info.latency < 100 { "Good" } 
                                     else { "Poor" }}
                                </div>
                            </div>
                        </div>

                        <div class="info-card health-info">
                            <div class="info-icon">{"HLT"}</div>
                            <div class="info-content">
                                <div class="info-label">{"Health Status"}</div>
                                <div class={format!("info-value health-status {}", 
                                    if server_info.is_healthy { "healthy" } else { "unhealthy" }
                                )}>
                                    { if server_info.is_healthy { "Healthy" } else { "Unhealthy" } }
                                </div>
                            </div>
                        </div>

                        <div class="info-card enabled-info">
                            <div class="info-icon">{"PWR"}</div>
                            <div class="info-content">
                                <div class="info-label">{"Server State"}</div>
                                <div class={format!("info-value power-status {}", 
                                    if server_info.enabled { "enabled" } else { "disabled" }
                                )}>
                                    { if server_info.enabled { "Enabled" } else { "Disabled" } }
                                </div>
                            </div>
                        </div>
                    </div>

                    {if !server_info.motd.is_empty() {
                        html! {
                            <div class="motd-section">
                                <div class="motd-label">{"Message of the Day"}</div>
                                <div class="motd-content">{ &server_info.motd }</div>
                            </div>
                        }
                    } else {
                        html! {}
                    }}
                </div>
            </div>

            <div class="card metrics-card">
                <div class="card-header">
                    <h2>{ "Live Metrics" }</h2>
                    <div class="metrics-status">
                        <span class="status-indicator"></span>
                        <span class="last-update">{ format!("Updated: {}", 
                            if metrics.timestamp.is_empty() { "Never".to_string() } else { 
                                let time = &metrics.timestamp[11..19];
                                format!("{}:{}", &time[0..5], &time[6..8])
                            }
                        ) }</span>
                    </div>
                </div>
                <div class="card-body metrics-body">
                    /* Performance Section */
                    <div class="metrics-section">
                        <h3 class="section-title">{"Performance"}</h3>
                        <div class="metrics-grid">
                            <div class="metric-card tps-card">
                                <div class="metric-icon">{"TPS"}</div>
                                <div class="metric-content">
                                    <div class="metric-label">{"TPS"}</div>
                                    <div class="metric-value">{format!("{:.1}", metrics.tps)}</div>
                                    <div class="metric-subtitle">{format!("{:.0}% efficiency", metrics.tps_percent)}</div>
                                </div>
                                <div class="metric-gauge">
                                    <div class="gauge-bg"></div>
                                    <div class="gauge-fill tps-gauge" style={format!("--percentage: {}%", metrics.tps_percent)}></div>
                                </div>
                            </div>

                            <div class="metric-card cpu-card">
                                <div class="metric-icon">{"CPU"}</div>
                                <div class="metric-content">
                                    <div class="metric-label">{"Process CPU"}</div>
                                    <div class="metric-value">{format!("{:.1}%", metrics.process_cpu_percent)}</div>
                                    <div class="metric-subtitle">{"Server usage"}</div>
                                </div>
                                <div class="metric-progress">
                                    <div class="progress-bg"></div>
                                    <div class="progress-fill cpu-progress" style={format!("width: {}%", metrics.process_cpu_percent)}></div>
                                </div>
                            </div>
                        </div>
                    </div>

                    /* Memory Section */
                    <div class="metrics-section">
                        <h3 class="section-title">{"Memory"}</h3>
                        <div class="metrics-grid">
                            <div class="metric-card memory-card">
                                <div class="metric-icon">{"RAM"}</div>
                                <div class="metric-content">
                                    <div class="metric-label">{"Server RAM"}</div>
                                    <div class="metric-value">{format!("{:.1}GB", metrics.memory_used_gb)}</div>
                                    <div class="metric-subtitle">{format!("/ {:.1}GB ({:.0}%)", metrics.memory_max_gb, metrics.memory_percent)}</div>
                                </div>
                                <div class="metric-progress">
                                    <div class="progress-bg"></div>
                                    <div class="progress-fill memory-progress" style={format!("width: {}%", metrics.memory_percent)}></div>
                                </div>
                            </div>

                            <div class="metric-card system-memory-card">
                                <div class="metric-icon">{"SYS"}</div>
                                <div class="metric-content">
                                    <div class="metric-label">{"System RAM"}</div>
                                    <div class="metric-value">{format!("{:.1}GB", metrics.system_memory_used_gb)}</div>
                                    <div class="metric-subtitle">{format!("/ {:.1}GB ({:.0}%)", metrics.system_memory_total_gb, metrics.system_memory_percent)}</div>
                                </div>
                                <div class="metric-progress">
                                    <div class="progress-bg"></div>
                                    <div class="progress-fill system-memory-progress" style={format!("width: {}%", metrics.system_memory_percent)}></div>
                                </div>
                            </div>
                        </div>
                    </div>

                    /* System Section */
                    <div class="metrics-section">
                        <h3 class="section-title">{"System"}</h3>
                        <div class="metrics-grid single-row">
                            <div class="metric-card system-cpu-card">
                                <div class="metric-icon">{"SYS"}</div>
                                <div class="metric-content">
                                    <div class="metric-label">{"System CPU"}</div>
                                    <div class="metric-value">{format!("{:.1}%", metrics.system_cpu_percent)}</div>
                                    <div class="metric-subtitle">{"Overall usage"}</div>
                                </div>
                                <div class="metric-progress">
                                    <div class="progress-bg"></div>
                                    <div class="progress-fill system-cpu-progress" style={format!("width: {}%", metrics.system_cpu_percent)}></div>
                                </div>
                            </div>

                            <div class="metric-card server-info-mini">
                                <div class="metric-icon">{"IP"}</div>
                                <div class="metric-content">
                                    <div class="metric-label">{"Server IP"}</div>
                                    <div class="metric-value server-ip">{
                                        if metrics.server_ip.is_empty() { "Loading...".to_string() } else { metrics.server_ip.clone() }
                                    }</div>
                                    <div class="metric-subtitle">{"Network address"}</div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        {if *show_delete_modal {
            html! {
                <div class="modal-overlay" onclick={close_delete_modal.clone()}>
                    <div class="modal-content delete-modal" onclick={Callback::from(|e: MouseEvent| e.stop_propagation())}>
                        <div class="modal-header">
                            <h3>{ "Confirm Server Deletion" }</h3>
                            <button class="modal-close" onclick={close_delete_modal.clone()}>{"×"}</button>
                        </div>
                        <div class="modal-body">
                            <p class="warning-text">
                                { "Are you sure you want to delete this server?" }
                            </p>
                            <p class="server-info">
                                <strong>{ "Server: " }</strong>
                                <span class="server-name">{ &server_info.name }</span>
                            </p>
                            <p class="warning-subtext">
                                { "This action cannot be undone. The server will be permanently removed from your network." }
                            </p>
                        </div>
                        <div class="modal-footer">
                            <button class="btn btn-secondary" onclick={close_delete_modal}>{"Cancel"}</button>
                            <button class="btn btn-danger-confirm" onclick={confirm_delete}>
                                <span class="btn-icon">{ "Delete" }</span>
                                { "Yes, Delete Server" }
                            </button>
                        </div>
                    </div>
                </div>
            }
        } else {
            html! {}
        }}

        {if !response.is_empty() {
            html! {
                <div class="response-toast">
                    <div class="toast-content">
                        <h4>{"Server Action"}</h4>
                        <p>{(*response).clone()}</p>
                    </div>
                </div>
            }
        } else {
            html! {}
        }}
    </div>
}
}