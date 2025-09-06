use gloo_console::log;
use gloo_timers::callback::{Timeout, Interval};
use yew::prelude::*;
use yew_router::prelude::*;
use serde::Deserialize;
use chrono::{DateTime, Utc};
use crate::utils::{api_get, api_post, ApiError};
use crate::Route;

#[derive(Deserialize, Clone, PartialEq, Default)]
#[allow(dead_code)]
pub struct ServerListResponse {
    pub servers: Vec<ServerInfo>,
    pub success: bool,
    pub message: String,
    #[serde(rename = "totalServers")]
    pub total_servers: i32,
    pub timestamp: String,
}

#[derive(Deserialize, Clone, PartialEq, Default)]
#[allow(dead_code)]
pub struct ServerInfo {
    pub name: String,
    #[serde(rename = "host")]
    pub serverip: String,    
    pub port: i32,
    pub status: String,
    pub enabled: bool,
    #[serde(rename = "currentPlayers")]
    pub current_players: i32,
    #[serde(rename = "maxPlayers")]
    pub max_players: i32,
    #[serde(rename = "loadStatus")]
    pub load_status: String,
    pub motd: String,
    #[serde(rename = "serverTimestamp")]
    pub server_timestamp: String,
    #[serde(rename = "loadPercentage")]
    pub load_percentage: f64,
    pub latency: i32,
    pub version: String,
    #[serde(rename = "isHealthy")]
    pub is_healthy: bool,
}

// Helper function to validate IP address or hostname
fn is_valid_ip_or_hostname(input: &str) -> bool {
    if input.is_empty() {
        return false;
    }
    
    // Check if it's a valid IPv4 address
    if is_valid_ipv4(input) {
        return true;
    }
    
    // Check if it's a valid hostname (basic validation)
    is_valid_hostname(input)
}

fn is_valid_ipv4(ip: &str) -> bool {
    let parts: Vec<&str> = ip.split('.').collect();
    if parts.len() != 4 {
        return false;
    }
    
    for part in parts {
        if let Ok(_num) = part.parse::<u8>() {
            // Valid u8 range (0-255)
            continue;
        } else {
            return false;
        }
    }
    true
}

fn is_valid_hostname(hostname: &str) -> bool {
    // Basic hostname validation
    if hostname.len() > 253 || hostname.is_empty() {
        return false;
    }
    
    // Check for valid characters and structure
    hostname.chars().all(|c| c.is_alphanumeric() || c == '.' || c == '-') &&
    !hostname.starts_with('.') &&
    !hostname.ends_with('.') &&
    !hostname.starts_with('-') &&
    !hostname.ends_with('-')
}

#[function_component(Servers)]
pub fn servers() -> Html {
    let response = use_state(|| String::new());
    let response_type = use_state(|| "info".to_string());
    let show_add_modal = use_state(|| false);
    let new_server_id = use_state(|| String::new());
    let new_server_name = use_state(|| String::new());
    let new_server_ip = use_state(|| String::new());
    let new_server_port = use_state(|| String::new());
    let navigator = use_navigator().unwrap();
    let servers = use_state(Vec::new);
    let is_loading = use_state(|| true);
    let action_loading = use_state(|| None::<String>);
    let last_updated = use_state(|| None::<String>);

    let fetch_servers_background = {
        let servers = servers.clone();
        let last_updated = last_updated.clone();
        Callback::from(move |_: ()| {
            api_get("http://127.0.0.1:8080/server/list", {
                let servers = servers.clone();
                let last_updated = last_updated.clone();
                Callback::from(move |data: String| {
                    log!("Background refresh - Raw server list response:", data.clone());
                    match serde_json::from_str::<ServerListResponse>(&data) {
                        Ok(response) => {
                            log!("Background refresh - Parsed server list successfully - count:", response.servers.len().to_string());
                            servers.set(response.servers);
                            last_updated.set(Some(Utc::now().format("%H:%M:%S UTC").to_string()));
                        }
                        Err(e) => {
                            log!("Background refresh - Error parsing server list:", e.to_string());
                            log!("Background refresh - Raw data was:", data);
                        }
                    }
                })
            });
        })
    };

    let fetch_servers = {
        let servers = servers.clone();
        let is_loading = is_loading.clone();
        let last_updated = last_updated.clone();
        Callback::from(move |_: ()| {
            is_loading.set(true);
            api_get("http://127.0.0.1:8080/server/list", {
                let servers = servers.clone();
                let is_loading = is_loading.clone();
                let last_updated = last_updated.clone();
                Callback::from(move |data: String| {
                    log!("Raw server list response:", data.clone());
                    match serde_json::from_str::<ServerListResponse>(&data) {
                        Ok(response) => {
                            log!("Parsed server list successfully - count:", response.servers.len().to_string());
                            servers.set(response.servers);
                            last_updated.set(Some(Utc::now().format("%H:%M:%S UTC").to_string()));
                        }
                        Err(e) => {
                            log!("Error parsing server list:", e.to_string());
                            log!("Raw data was:", data);
                        }
                    }
                    is_loading.set(false);
                })
            });
        })
    };

    {
        let fetch_servers = fetch_servers.clone();
        use_effect_with((), move |_| {
            fetch_servers.emit(());
            || ()
        });
    }

    // every 20s
    {
        let fetch_servers_background = fetch_servers_background.clone();
        use_effect_with((), move |_| {
            let interval = Interval::new(20000, move || {
                log!("Performing periodic background refresh...");
                fetch_servers_background.emit(());
            });
            
            move || {
                drop(interval);
            }
        });
    }

    // Auto-clear response toast after 5 seconds
    {
        let response = response.clone();
        let response_type = response_type.clone();
        use_effect_with((response.clone(), response_type.clone()), move |(response_state, _response_type_state)| {
            if !response_state.is_empty() {
                let response_clear = response.clone();
                let response_type_clear = response_type.clone();
                let timeout = Timeout::new(5000, move || {
                    response_clear.set(String::new());
                    response_type_clear.set("info".to_string());
                });
                timeout.forget();
            }
            || ()
        });
    }

    fn format_timestamp(ts: &str) -> String {
        match DateTime::parse_from_rfc3339(ts) {
            Ok(dt) => dt.with_timezone(&Utc).format("%Y-%m-%d %H:%M:%S UTC").to_string(),
            Err(_) => ts.to_string(),
        }
    }

    fn extract_motd_content(motd: &str) -> String {
        if let Some(start) = motd.find("content=\"") {
            let rest = &motd[start + 9..];
            if let Some(end) = rest.find('"') {
                return rest[..end].to_string();
            }
        }
        motd.to_string()
    }

    let create_enable_handler = {
        let response = response.clone();
        let response_type = response_type.clone();
        let fetch_servers = fetch_servers.clone();
        let action_loading = action_loading.clone();
        Callback::from(move |server_id: String| {
            action_loading.set(Some(format!("enabling_{}", server_id)));
            let server_data = format!(r#"{{"name":"{}"}}"#, server_id);
            
            api_post("http://127.0.0.1:8080/server/enable", Some(&server_data), {
                let response = response.clone();
                let response_type = response_type.clone();
                let fetch_servers = fetch_servers.clone();
                let action_loading = action_loading.clone();
                Callback::from(move |result: Result<String, ApiError>| {
                    action_loading.set(None);
                    match result {
                        Ok(_) => {
                            response.set("Server enabled successfully!".to_string());
                            response_type.set("success".to_string());
                            fetch_servers.emit(());
                        }
                        Err(e) => {
                            let (error_message, error_type) = match &e {
                                ApiError::Request { status, .. } if *status == 404 => 
                                    ("Server not found. It may have been removed.".to_string(), "warning".to_string()),
                                _ => ("Failed to enable server. Please try again.".to_string(), "error".to_string()),
                            };
                            response.set(error_message);
                            response_type.set(error_type);
                        }
                    }
                })
            });
        })
    };

    let create_disable_handler = {
        let response = response.clone();
        let response_type = response_type.clone();
        let fetch_servers = fetch_servers.clone();
        let action_loading = action_loading.clone();
        Callback::from(move |server_id: String| {
            action_loading.set(Some(format!("disabling_{}", server_id)));
            let server_data = format!(r#"{{"name":"{}"}}"#, server_id);
            
            api_post("http://127.0.0.1:8080/server/disable", Some(&server_data), {
                let response = response.clone();
                let response_type = response_type.clone();
                let fetch_servers = fetch_servers.clone();
                let action_loading = action_loading.clone();
                Callback::from(move |result: Result<String, ApiError>| {
                    action_loading.set(None);
                    match result {
                        Ok(_) => {
                            response.set("Server disabled successfully!".to_string());
                            response_type.set("success".to_string());
                            fetch_servers.emit(());
                        }
                        Err(e) => {
                            let (error_message, error_type) = match &e {
                                ApiError::Request { status, .. } if *status == 404 => 
                                    ("Server not found. It may have been removed.".to_string(), "warning".to_string()),
                                _ => ("Failed to disable server. Please try again.".to_string(), "error".to_string()),
                            };
                            response.set(error_message);
                            response_type.set(error_type);
                        }
                    }
                })
            });
        })
    };

    let add_server_click = {
        let show_add_modal = show_add_modal.clone();
        Callback::from(move |_: MouseEvent| {
            show_add_modal.set(true);
        })
    };

    let manual_refresh_click = {
        let fetch_servers = fetch_servers.clone();
        Callback::from(move |_: MouseEvent| {
            fetch_servers.emit(());
        })
    };

    let close_modal = {
        let show_add_modal = show_add_modal.clone();
        let new_server_id = new_server_id.clone();
        let new_server_name = new_server_name.clone();
        let new_server_ip = new_server_ip.clone();
        let new_server_port = new_server_port.clone();
        Callback::from(move |_: MouseEvent| {
            show_add_modal.set(false);
            new_server_id.set(String::new());
            new_server_name.set(String::new());
            new_server_ip.set(String::new());
            new_server_port.set(String::new());
        })
    };

    let submit_server = {
        let response = response.clone();
        let response_type = response_type.clone();
        let show_add_modal = show_add_modal.clone();
        let new_server_id = new_server_id.clone();
        let new_server_name = new_server_name.clone();
        let new_server_ip = new_server_ip.clone();
        let new_server_port = new_server_port.clone();
        let fetch_servers = fetch_servers.clone();

        Callback::from(move |_: MouseEvent| {
            log!("=== SUBMIT BUTTON CLICKED ===");
            log!("ID:", (*new_server_id).clone());
            log!("Name:", (*new_server_name).clone());
            log!("IP:", (*new_server_ip).clone());
            log!("Port:", (*new_server_port).clone());

            // Validate all fields are filled
            let server_id = (*new_server_id).trim();
            let server_name = (*new_server_name).trim();
            let server_ip = (*new_server_ip).trim();
            let server_port = (*new_server_port).trim();

            // Check for empty fields
            if server_id.is_empty() {
                response.set("Server ID is required. Please enter a server ID.".to_string());
                response_type.set("warning".to_string());
                return;
            }

            if server_name.is_empty() {
                response.set("Server name is required. Please enter a server name.".to_string());
                response_type.set("warning".to_string());
                return;
            }

            if server_ip.is_empty() {
                response.set("Server IP address is required. Please enter a valid IP address.".to_string());
                response_type.set("warning".to_string());
                return;
            }

            if server_port.is_empty() {
                response.set("Server port is required. Please enter a port number.".to_string());
                response_type.set("warning".to_string());
                return;
            }

            // Validate port number
            let port: u16 = match server_port.parse() {
                Ok(p) if p > 0 => p,
                _ => {
                    response.set("Invalid port number. Please enter a valid port between 1 and 65535.".to_string());
                    response_type.set("warning".to_string());
                    return;
                }
            };

            // Basic IP validation (simple check for valid format)
            if !is_valid_ip_or_hostname(server_ip) {
                response.set("Invalid IP address or hostname format. Please enter a valid IP address or hostname.".to_string());
                response_type.set("warning".to_string());
                return;
            }

            let max_players: u32 = 500;
            let server_data = format!(
                r#"{{"name":"{}","ip":"{}","port":{},"maxPlayers":{}}}"#,
                server_id, server_ip, port, max_players
            );

            log!("Formatted payload:", server_data.clone());

            api_post("http://127.0.0.1:8080/server/add", Some(&server_data), {
                let response = response.clone();
                let response_type = response_type.clone();
                let fetch_servers = fetch_servers.clone();
                Callback::from(move |result: Result<String, ApiError>| {
                    log!("API Response received:", format!("{:?}", result));
                    match result {
                        Ok(data) => {
                            log!("Success response:", data.clone());
                            response.set("Server added successfully!".to_string());
                            response_type.set("success".to_string());
                            fetch_servers.emit(());
                        }
                        Err(e) => {
                            log!("Error response:", format!("{:?}", e));
                            log!("Error string:", e.to_string());
                            
                            // Categorize error types
                            let (error_message, error_type) = match &e {
                                ApiError::Request { status, message, is_structured } => {
                                    match *status {
                                        409 => {
                                            if message.contains("already exists") {
                                                ("A server with this name already exists. Please choose a different name.".to_string(), "warning".to_string())
                                            } else {
                                                (message.clone(), "error".to_string())
                                            }
                                        }
                                        400 => ("Invalid server configuration. Please check your inputs.".to_string(), "warning".to_string()),
                                        500 => ("Server error occurred. Please try again later.".to_string(), "error".to_string()),
                                        _ => {
                                            if *is_structured {
                                                (message.clone(), "error".to_string())
                                            } else {
                                                (format!("HTTP Error {}: {}", status, message), "error".to_string())
                                            }
                                        }
                                    }
                                }
                                ApiError::Network(_) => ("Network connection failed. Please check your connection.".to_string(), "error".to_string()),
                                ApiError::Parse(_) => ("Failed to process server response.".to_string(), "error".to_string()),
                            };
                            
                            response.set(error_message);
                            response_type.set(error_type);
                        }
                    }
                })
            });

            show_add_modal.set(false);
            new_server_id.set(String::new());
            new_server_name.set(String::new());
            new_server_ip.set(String::new());
            new_server_port.set(String::new());
        })
    };

    html! { 
        <div class="servers-page">
            <div class="page-header">
                <div>
                    <h1>{"Server List"}</h1>
                    <p>{"Manage your Minecraft servers"}</p>
                    {if let Some(updated) = last_updated.as_ref() {
                        html! {
                            <small class="last-updated">
                                {"Last updated: "}{updated}{" (Auto-refreshes every 15s)"}
                            </small>
                        }
                    } else {
                        html! {}
                    }}
                </div>
                <div class="header-actions">
                    <button class="btn btn-secondary" onclick={manual_refresh_click} disabled={*is_loading}>
                        {if *is_loading { "🔄 Refreshing..." } else { "🔄 Refresh" }}
                    </button>
                    <button class="btn btn-success" onclick={add_server_click}>{"➕ Add Server"}</button>
                </div>
            </div>
            <div class="servers-grid">
                {if *is_loading {
                    html! {
                        <div class="loading-container">
                            <div class="loading-spinner"></div>
                            <p class="loading-text">{"Loading servers..."}</p>
                        </div>
                    }
                } else {
                    html! {
                        <>
                        {for servers.iter().map(|server| {
                            let is_disabled = !server.enabled;
                            let status_class = match server.status.as_str() {
                                "online" => "online",
                                "offline" => "offline", 
                                "disabled" => "disabled",
                                _ => "unknown"
                            };
                            
                            html! {
                                <div class={format!("server-card {}", if is_disabled { "server-disabled" } else { "" })} key={server.name.clone()}>
                                    <div class="server-header">
                                        <h3 class="server-name">{&server.name}</h3>
                                        <span class={format!("server-status {}", status_class)}>{&server.status}</span>
                                    </div>
                                        <div class="server-info">
                                            <div class="info-row">
                                                <span class="label">{"Address:"}</span>
                                                <span class="value">{format!("{}:{}", server.serverip, server.port)}</span>
                                            </div>
                                            <div class="info-row">
                                                <span class="label">{"Status:"}</span>
                                                <span class={format!("value status-{}", if server.enabled { "enabled" } else { "disabled" })}>
                                                    {if server.enabled { "Enabled" } else { "Disabled" }}
                                                </span>
                                            </div>
                                            <div class="info-row">
                                                <span class="label">{"Players:"}</span>
                                                <span class="value">{format!("{}/{}", server.current_players, if server.max_players == -1 { "∞".to_string() } else { server.max_players.to_string() })}</span>
                                            </div>
                                            <div class="info-row">
                                                <span class="label">{"Load:"}</span>
                                                <span class="value">{&server.load_status}</span>
                                            </div>
                                            <div class="info-row">
                                                <span class="label">{"Load %:"}</span>
                                                <span class="value">{format!("{:.1}%", server.load_percentage)}</span>
                                            </div>
                                            <div class="info-row">
                                                <span class="label">{"Latency:"}</span>
                                                <span class="value">{if server.latency == -1 { "N/A".to_string() } else { format!("{} ms", server.latency) }}</span>
                                            </div>
                                            <div class="info-row">
                                                <span class="label">{"Version:"}</span>
                                                <span class="value">{&server.version}</span>
                                            </div>
                                            <div class="info-row">
                                                <span class="label">{"Healthy:"}</span>
                                                <span class={format!("value healthy-{}", if server.is_healthy { "yes" } else { "no" })}>
                                                    {if server.is_healthy { "Yes" } else { "No" }}
                                                </span>
                                            </div>
                                                <div class="info-row">
                                                    <span class="label">{"MOTD:"}</span>
                                                    <span class="value">{extract_motd_content(&server.motd)}</span>
                                                </div>
                                            <div class="info-row">
                                                <span class="label">{"Timestamp:"}</span>
                                                <span class="value">{format_timestamp(&server.server_timestamp)}</span>
                                            </div>
                                        </div>
                                    <div class="server-actions">
                                        <button 
                                            class={format!("btn btn-sm btn-primary {}", if is_disabled { "btn-disabled" } else { "" })}
                                            disabled={is_disabled}
                                            onclick={{
                                                if !is_disabled {
                                                    let server_name = server.name.clone();
                                                    let navigator = navigator.clone();
                                                    Callback::from(move |_: MouseEvent| {
                                                        navigator.push(&Route::ServerDetail { id: server_name.clone() });
                                                    })
                                                } else {
                                                    Callback::from(|_: MouseEvent| {})
                                                }
                                            }}
                                        >{"📊 Details"}</button>
                                        
                                        {if !server.enabled {
                                            let is_enabling = action_loading.as_ref().map_or(false, |loading| loading == &format!("enabling_{}", server.name));
                                            html! {
                                                <button 
                                                    class={format!("btn btn-sm btn-success {}", if is_enabling { "btn-loading" } else { "" })}
                                                    disabled={is_enabling}
                                                    onclick={{
                                                        let server_id = server.name.clone();
                                                        let enable_handler = create_enable_handler.clone();
                                                        Callback::from(move |_: MouseEvent| {
                                                            enable_handler.emit(server_id.clone());
                                                        })
                                                    }}
                                                >{if is_enabling { "⏳ Enabling..." } else { "✅ Enable" }}</button>
                                            }
                                        } else {
                                            let is_disabling = action_loading.as_ref().map_or(false, |loading| loading == &format!("disabling_{}", server.name));
                                            html! {
                                                <button 
                                                    class={format!("btn btn-sm btn-warning {}", if is_disabling { "btn-loading" } else { "" })}
                                                    disabled={is_disabling}
                                                    onclick={{
                                                        let server_id = server.name.clone();
                                                        let disable_handler = create_disable_handler.clone();
                                                        Callback::from(move |_: MouseEvent| {
                                                            disable_handler.emit(server_id.clone());
                                                        })
                                                    }}
                                                >{if is_disabling { "⏳ Disabling..." } else { "❌ Disable" }}</button>
                                            }
                                        }}
                                    </div>
                                </div>
                            }
                        })}
                        </>
                    }
                }}
            </div>
            
            {if *show_add_modal {
                html! {
                    <div class="modal-overlay" onclick={close_modal.clone()}>
                        <div class="modal-content" onclick={|e: MouseEvent| e.stop_propagation()}>
                            <div class="modal-header">
                                <h2>{"Add New Server"}</h2>
                                <button class="modal-close" onclick={close_modal.clone()}>{"×"}</button>
                            </div>
                            <div class="modal-body">
                                <div class="form-group">
                                    <label>{"Server ID"} <span class="required">{"*"}</span></label>
                                    <input 
                                        type="text" 
                                        placeholder="e.g., survival-2" 
                                        value={(*new_server_id).clone()}
                                        required=true
                                        oninput={{
                                            let new_server_id = new_server_id.clone();
                                            Callback::from(move |e: InputEvent| {
                                                let input: web_sys::HtmlInputElement = e.target_unchecked_into();
                                                new_server_id.set(input.value());
                                            })
                                        }}
                                    />
                                    <small class="field-hint">{"Unique identifier for this server"}</small>
                                </div>
                                <div class="form-group">
                                    <label>{"Server Name"} <span class="required">{"*"}</span></label>
                                    <input 
                                        type="text" 
                                        placeholder="e.g., Survival World 2" 
                                        value={(*new_server_name).clone()}
                                        required=true
                                        oninput={{
                                            let new_server_name = new_server_name.clone();
                                            Callback::from(move |e: InputEvent| {
                                                let input: web_sys::HtmlInputElement = e.target_unchecked_into();
                                                new_server_name.set(input.value());
                                            })
                                        }}
                                    />
                                    <small class="field-hint">{"Display name for this server"}</small>
                                </div>
                                <div class="form-group">
                                    <label>{"Server IP"} <span class="required">{"*"}</span></label>
                                    <input 
                                        type="text" 
                                        placeholder="e.g., 192.168.1.104 or minecraft.example.com" 
                                        value={(*new_server_ip).clone()}
                                        required=true
                                        oninput={{
                                            let new_server_ip = new_server_ip.clone();
                                            Callback::from(move |e: InputEvent| {
                                                let input: web_sys::HtmlInputElement = e.target_unchecked_into();
                                                new_server_ip.set(input.value());
                                            })
                                        }}
                                    />
                                    <small class="field-hint">{"IP address or hostname of the server"}</small>
                                </div>
                                <div class="form-group">
                                    <label>{"Port"} <span class="required">{"*"}</span></label>
                                    <input 
                                        type="number" 
                                        placeholder="e.g., 25565" 
                                        value={(*new_server_port).clone()}
                                        required=true
                                        min="1"
                                        max="65535"
                                        oninput={{
                                            let new_server_port = new_server_port.clone();
                                            Callback::from(move |e: InputEvent| {
                                                let input: web_sys::HtmlInputElement = e.target_unchecked_into();
                                                new_server_port.set(input.value());
                                            })
                                        }}
                                    />
                                    <small class="field-hint">{"Port number (1-65535)"}</small>
                                </div>
                            </div>
                            <div class="modal-footer">
                                <button class="btn btn-secondary" onclick={close_modal}>{"Cancel"}</button>
                                <button class="btn btn-success" onclick={submit_server}>{"Add Server"}</button>
                            </div>
                        </div>
                    </div>
                }
            } else {
                html! {}
            }}
            
            {if !response.is_empty() {
                let toast_class = format!("toast-content {}", (*response_type).clone());
                let toast_title = match (*response_type).as_str() {
                    "success" => "Success",
                    "error" => "Error", 
                    "warning" => "Warning",
                    _ => "Server Action"
                };
                
                html! {
                    <div class="response-toast">
                        <div class={toast_class}>
                            <h4>{toast_title}</h4>
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