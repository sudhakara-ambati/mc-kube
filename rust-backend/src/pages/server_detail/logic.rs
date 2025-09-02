use yew::{function_component, html, Html, use_state, use_effect_with, Callback, MouseEvent, Properties};
use yew_router::prelude::*;
use crate::utils::{api_get, api_post, ApiError};
use crate::Route;
use wasm_bindgen_futures::spawn_local;
use gloo_net::http::Request;
use serde::Deserialize;
use gloo_console::log;
use gloo_timers::callback::Interval;
use crate::pages::servers::logic::{ServerInfo};

#[derive(Properties, PartialEq)]
pub struct ServerDetailProps {
    pub server_id: String,
}

#[derive(Clone, PartialEq, Deserialize, Default)]
pub struct ServerMetrics {
    #[serde(rename = "serverIp", default)]
    pub server_ip: String,
    #[serde(rename = "systemCpuPercent", default)]
    pub system_cpu_percent: f64,
    #[serde(rename = "processCpuPercent", default)]
    pub process_cpu_percent: f64,
    #[serde(rename = "memoryUsedGB", default)]
    pub memory_used_gb: f64,
    #[serde(rename = "memoryMaxGB", default)]
    pub memory_max_gb: f64,
    #[serde(rename = "memoryPercent", default)]
    pub memory_percent: f64,
    #[serde(rename = "sysUsedGB", default)]
    pub sys_used_gb: f64,
    #[serde(rename = "sysTotalGB", default)]
    pub sys_total_gb: f64,
    #[serde(rename = "sysMemPercent", default)]
    pub sys_mem_percent: f64,
    #[serde(default)]
    pub tps: f64,
    #[serde(rename = "tpsPercent", default)]
    pub tps_percent: f64,
}

#[function_component(ServerDetail)]
pub fn server_detail(props: &ServerDetailProps) -> Html {
    let server_info = use_state(ServerInfo::default);
    let metrics = use_state(ServerMetrics::default);
    let logs = use_state(|| String::from("[14:23:45] [INFO] Player Steve joined the game\n[14:24:12] [INFO] Player Alex left the game\n[14:24:58] [WARN] Can't keep up! Running 2043ms behind\n[14:25:33] [INFO] Saving chunks for level 'ServerLevel[world]'\n[14:26:15] [INFO] Player Bob joined the game"));
    let players = use_state(|| String::from("Online Players (8/20):\n• Steve - 45 mins\n• Alex - 1h 23 mins\n• Bob - 12 mins\n• Charlie - 2h 15 mins\n• Diana - 38 mins\n• Eve - 1h 7 mins\n• Frank - 26 mins\n• Grace - 3h 2 mins"));
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
                let request_url = format!("/api/servers/{}", server_id);
                match Request::get(&request_url).send().await {
                    Ok(resp) => match resp.json::<ServerInfo>().await {
                        Ok(data) => {
                            server_info.set(data);
                        }
                        Err(e) => log!("Failed to parse server info:", e.to_string()),
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
            let server_ip = server_info.serverip.clone();
            let is_ip_loaded = !server_ip.is_empty();

            let interval = if is_ip_loaded {
                let fetch_metrics = move || {
                    let metrics = metrics.clone();
                    let server_ip = server_ip.clone();
                    spawn_local(async move {
                        let request_url = format!("/api/servers/{}/metrics", server_ip);
                        match Request::get(&request_url).send().await {
                            Ok(resp) => match resp.json::<ServerMetrics>().await {
                                Ok(data) => metrics.set(data),
                                Err(e) => log!("Failed to parse metrics JSON:", e.to_string()),
                            },
                            Err(e) => log!("Failed to fetch metrics:", e.to_string()),
                        }
                    });
                };

                fetch_metrics();
                Some(Interval::new(2000, fetch_metrics))
            } else {
                None
            };

            move || {
                drop(interval);
            }
        });
    }

    let refresh_logs = {
        let logs = logs.clone();
        let server_id = props.server_id.clone();
        Callback::from(move |_: MouseEvent| {
            api_get(&format!("http://localhost:8080/servers/{}/logs", server_id), {
                let logs = logs.clone();
                Callback::from(move |data| logs.set(data))
            });
        })
    };

    let refresh_players = {
        let players = players.clone();
        let server_id = props.server_id.clone();
        Callback::from(move |_: MouseEvent| {
            api_get(&format!("http://localhost:8080/servers/{}/players", server_id), {
                let players = players.clone();
                Callback::from(move |data| players.set(data))
            });
        })
    };

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

    let kick_player = {
        let response = response.clone();
        let server_id = props.server_id.clone();
        Callback::from(move |_: MouseEvent| {
            api_post(&format!("http://localhost:8080/servers/{}/kick", server_id), Some(r#"{"player":"badplayer"}"#), {
                let response = response.clone();
                Callback::from(move |result: Result<String, ApiError>| {
                    match result {
                        Ok(data) => response.set(format!("Success: {}", data)),
                        Err(e) => response.set(format!("Error: {}", e)),
                    }
                })
            });
        })
    };

    let ban_player = {
        let response = response.clone();
        let server_id = props.server_id.clone();
        Callback::from(move |_: MouseEvent| {
            api_post(&format!("http://localhost:8080/servers/{}/ban", server_id), Some(r#"{"player":"cheater","reason":"hacking"}"#), {
                let response = response.clone();
                Callback::from(move |result: Result<String, ApiError>| {
                    match result {
                        Ok(data) => response.set(format!("Success: {}", data)),
                        Err(e) => response.set(format!("Error: {}", e)),
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
                        <span class="btn-icon">{ "🗑️" }</span>
                        { "Delete Server" }
                    </button>
                </div>
            </div>

            <div class="detail-grid">
                <div class="card metrics-card">
                    <div class="card-header">
                        <h2>{ "Live Metrics" }</h2>
                        <p class="card-subtitle">{ format!("IP: {}", metrics.server_ip.clone()) }</p>
                    </div>
                    <div class="card-body">
                        <div class="metric-item">
                            <span class="metric-label">{ "TPS" }</span>
                            <div class="metric-value-bar">
                                <div class="bar" style={format!("width: {}%", metrics.tps_percent)}></div>
                                <span>{ format!("{:.1}", metrics.tps) }</span>
                            </div>
                        </div>
                        <div class="metric-item">
                            <span class="metric-label">{ "Process CPU" }</span>
                            <div class="metric-value-bar">
                                <div class="bar" style={format!("width: {}%", metrics.process_cpu_percent)}></div>
                                <span>{ format!("{:.1}%", metrics.process_cpu_percent) }</span>
                            </div>
                        </div>
                        <div class="metric-item">
                            <span class="metric-label">{ "System CPU" }</span>
                            <div class="metric-value-bar">
                                <div class="bar" style={format!("width: {}%", metrics.system_cpu_percent)}></div>
                                <span>{ format!("{:.1}%", metrics.system_cpu_percent) }</span>
                            </div>
                        </div>
                        <div class="metric-item">
                            <span class="metric-label">{ "Process RAM" }</span>
                            <div class="metric-value-bar">
                                <div class="bar" style={format!("width: {}%", metrics.memory_percent)}></div>
                                <span>{ format!("{:.2} / {:.2} GB", metrics.memory_used_gb, metrics.memory_max_gb) }</span>
                            </div>
                        </div>
                        <div class="metric-item">
                            <span class="metric-label">{ "System RAM" }</span>
                            <div class="metric-value-bar">
                                <div class="bar" style={format!("width: {}%", metrics.sys_mem_percent)}></div>
                                <span>{ format!("{:.2} / {:.2} GB", metrics.sys_used_gb, metrics.sys_total_gb) }</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="card logs-card">
                    <div class="card-header">
                        <h2>{ "Console Logs" }</h2>
                        <button class="btn btn-small" onclick={refresh_logs}>{ "Refresh" }</button>
                    </div>
                    <div class="card-body">
                        <pre class="logs-container">{ &*logs }</pre>
                    </div>
                </div>

                <div class="card players-card">
                    <div class="card-header">
                        <h2>{ "Player Management" }</h2>
                        <button class="btn btn-small" onclick={refresh_players}>{ "Refresh" }</button>
                    </div>
                    <div class="card-body">
                        <pre class="players-container">{ &*players }</pre>
                        <div class="player-actions">
                            <input type="text" placeholder="Player Name" />
                            <button class="btn btn-warning" onclick={kick_player.clone()}>{ "Kick" }</button>
                            <button class="btn btn-danger" onclick={ban_player.clone()}>{ "Ban" }</button>
                        </div>
                    </div>
                </div>
            </div>
                        {if *show_delete_modal {
                html! {
                    <div class="modal-overlay" onclick={close_delete_modal.clone()}>
                        <div class="modal-content delete-modal" onclick={Callback::from(|e: MouseEvent| e.stop_propagation())}>
                            <div class="modal-header">
                                <h3>{ "⚠️ Confirm Server Deletion" }</h3>
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
                                    <span class="btn-icon">{ "🗑️" }</span>
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