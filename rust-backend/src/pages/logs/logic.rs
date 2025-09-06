use gloo_console::log;
use gloo_timers::callback::Interval;
use yew::prelude::*;
use yew_router::prelude::*;
use serde::Deserialize;
use chrono::{DateTime, Utc};
use crate::utils::api_get;

#[derive(Deserialize, Clone, PartialEq, Default)]
#[allow(dead_code)]
pub struct LogsResponse {
    pub success: bool,
    pub count: i32,
    pub limit: i32,
    pub message: String,
    pub events: Vec<LogEvent>,
    pub timestamp: String,
}

#[derive(Deserialize, Clone, PartialEq, Default)]
#[allow(dead_code)]
pub struct LogEvent {
    pub id: String,
    #[serde(rename = "type")]
    pub event_type: String,
    pub message: String,
    pub timestamp: String,
    pub player_name: Option<String>,
    pub player_uuid: Option<String>,
    pub server_name: Option<String>,
    pub metadata: Option<serde_json::Value>,
}

fn format_timestamp(timestamp: &str) -> String {
    if let Ok(dt) = DateTime::parse_from_rfc3339(timestamp) {
        dt.format("%Y-%m-%d %H:%M:%S UTC").to_string()
    } else {
        timestamp.to_string()
    }
}

fn get_event_type_class(event_type: &str) -> &'static str {
    match event_type {
        // Player events
        "player_join" => "event-success",
        "player_leave" => "event-warning",
        "player_reconnect" => "event-info",
        
        // Server events
        "server_connect" => "event-success",
        "server_disconnect" => "event-warning",
        "server_offline" => "event-error",
        "server_online" => "event-success",
        "server_high_latency" => "event-warning",
        "server_status_check" => "event-info",
        
        // Queue events
        "queue_join" => "event-info",
        "queue_leave" => "event-info",
        "queue_remove_admin" => "event-warning",
        "queue_cleanup" => "event-system",
        "queue_position_update" => "event-info",
        
        // Transfer events
        "transfer_initiated" => "event-info",
        "transfer_completed" => "event-success",
        "transfer_failed" => "event-error",
        
        // Broadcast events
        "broadcast_all" => "event-info",
        "broadcast_server" => "event-info",
        "broadcast_failed" => "event-error",
        
        // Metrics events
        "metrics_collected" => "event-metrics",
        "metrics_failed" => "event-error",
        
        // System events
        "system_startup" => "event-success",
        "system_shutdown" => "event-warning",
        "system_config_change" => "event-system",
        "system_event" => "event-system",
        
        // API events
        "api_request" => "event-info",
        "api_error" => "event-error",
        
        // Error events
        "error" => "event-error",
        
        _ => "event-default",
    }
}

fn get_event_type_icon(event_type: &str) -> &'static str {
    match event_type {
        // Player events
        "player_join" => "👋",
        "player_leave" => "👋",
        "player_reconnect" => "🔄",
        
        // Server events
        "server_connect" => "🔗",
        "server_disconnect" => "�",
        "server_offline" => "❌",
        "server_online" => "✅",
        "server_high_latency" => "⚠️",
        "server_status_check" => "🔍",
        
        // Queue events
        "queue_join" => "⏳",
        "queue_leave" => "⏳",
        "queue_remove_admin" => "👑",
        "queue_cleanup" => "🧹",
        "queue_position_update" => "📊",
        
        // Transfer events
        "transfer_initiated" => "🚀",
        "transfer_completed" => "✅",
        "transfer_failed" => "❌",
        
        // Broadcast events
        "broadcast_all" => "📢",
        "broadcast_server" => "📢",
        "broadcast_failed" => "❌",
        
        // Metrics events
        "metrics_collected" => "📊",
        "metrics_failed" => "❌",
        
        // System events
        "system_startup" => "🚀",
        "system_shutdown" => "🔴",
        "system_config_change" => "⚙️",
        "system_event" => "⚙️",
        
        // API events
        "api_request" => "🌐",
        "api_error" => "❌",
        
        // Error events
        "error" => "🚨",
        
        _ => "📝",
    }
}

fn render_metadata(metadata: &serde_json::Value) -> Html {
    match metadata {
        serde_json::Value::Object(map) => {
            let items: Vec<Html> = map.iter().map(|(key, value)| {
                let value_str = match value {
                    serde_json::Value::String(s) => s.clone(),
                    serde_json::Value::Number(n) => n.to_string(),
                    serde_json::Value::Bool(b) => b.to_string(),
                    _ => serde_json::to_string(value).unwrap_or_default(),
                };
                html! {
                    <div class="metadata-item">
                        <span class="metadata-key">{key.clone()}{": "}</span>
                        <span class="metadata-value">{value_str}</span>
                    </div>
                }
            }).collect();
            
            html! {
                <div class="metadata-content">
                    {for items}
                </div>
            }
        }
        _ => html! { <span>{"No metadata"}</span> }
    }
}

#[function_component(Logs)]
pub fn logs() -> Html {
    let _navigator = use_navigator().unwrap();
    let logs = use_state(Vec::new);
    let is_loading = use_state(|| true);
    let last_updated = use_state(|| None::<String>);
    let filter_type = use_state(|| "all".to_string());
    let filter_server = use_state(|| "all".to_string());

    let fetch_logs_background = {
        let logs = logs.clone();
        let last_updated = last_updated.clone();
        Callback::from(move |_: ()| {
            api_get("http://127.0.0.1:8080/cluster/logs/", {
                let logs = logs.clone();
                let last_updated = last_updated.clone();
                Callback::from(move |data: String| {
                    log!("Background refresh - Raw logs response:", data.clone());
                    match serde_json::from_str::<LogsResponse>(&data) {
                        Ok(response) => {
                            log!("Background refresh - Parsed logs successfully - count:", response.events.len().to_string());
                            logs.set(response.events);
                            last_updated.set(Some(Utc::now().format("%H:%M:%S UTC").to_string()));
                        }
                        Err(e) => {
                            log!("Background refresh - Error parsing logs:", e.to_string());
                            log!("Background refresh - Raw data was:", data);
                        }
                    }
                })
            });
        })
    };

    let fetch_logs = {
        let logs = logs.clone();
        let is_loading = is_loading.clone();
        let last_updated = last_updated.clone();
        Callback::from(move |_: ()| {
            is_loading.set(true);
            api_get("http://127.0.0.1:8080/cluster/logs/", {
                let logs = logs.clone();
                let is_loading = is_loading.clone();
                let last_updated = last_updated.clone();
                Callback::from(move |data: String| {
                    log!("Raw logs response:", data.clone());
                    match serde_json::from_str::<LogsResponse>(&data) {
                        Ok(response) => {
                            log!("Parsed logs successfully - count:", response.events.len().to_string());
                            logs.set(response.events);
                            last_updated.set(Some(Utc::now().format("%H:%M:%S UTC").to_string()));
                        }
                        Err(e) => {
                            log!("Error parsing logs:", e.to_string());
                            log!("Raw data was:", data);
                        }
                    }
                    is_loading.set(false);
                })
            });
        })
    };

    // Initial fetch
    {
        let fetch_logs = fetch_logs.clone();
        use_effect_with((), move |_| {
            fetch_logs.emit(());
            || ()
        });
    }

    // Set up auto-refresh interval
    {
        let fetch_logs_background = fetch_logs_background.clone();
        use_effect_with((), move |_| {
            let interval = Interval::new(5000, move || {
                fetch_logs_background.emit(());
            });
            
            move || {
                interval.cancel();
            }
        });
    }

    let on_refresh = {
        let fetch_logs = fetch_logs.clone();
        Callback::from(move |_| {
            fetch_logs.emit(());
        })
    };

    let on_filter_type_change = {
        let filter_type = filter_type.clone();
        Callback::from(move |e: Event| {
            let input: web_sys::HtmlInputElement = e.target_unchecked_into();
            filter_type.set(input.value());
        })
    };

    let on_filter_server_change = {
        let filter_server = filter_server.clone();
        Callback::from(move |e: Event| {
            let input: web_sys::HtmlInputElement = e.target_unchecked_into();
            filter_server.set(input.value());
        })
    };

    let filtered_logs = {
        let filter_type_value = (*filter_type).clone();
        let filter_server_value = (*filter_server).clone();
        
        logs.iter().filter(|log| {
            let type_match = filter_type_value == "all" || log.event_type == filter_type_value;
            let server_match = filter_server_value == "all" || 
                log.server_name.as_ref().map_or(false, |name| name == &filter_server_value);
            type_match && server_match
        }).cloned().collect::<Vec<_>>()
    };

    let unique_servers: Vec<String> = {
        let mut servers: Vec<String> = logs.iter()
            .filter_map(|log| log.server_name.clone())
            .collect::<std::collections::HashSet<_>>()
            .into_iter()
            .collect();
        servers.sort();
        servers
    };

    let unique_types: Vec<String> = {
        let mut types: Vec<String> = logs.iter()
            .map(|log| log.event_type.clone())
            .collect::<std::collections::HashSet<_>>()
            .into_iter()
            .collect();
        types.sort();
        types
    };

    html! {
        <div class="logs-container">
            <div class="logs-page">
                <div class="page-header">
                    <h1>{"Cluster Logs"}</h1>
                    <div class="header-actions">
                        {if let Some(updated) = &*last_updated {
                            html! { <span class="last-updated">{"Last updated: "}{updated}</span> }
                        } else {
                            html! {}
                        }}
                        <button class="btn btn-secondary" onclick={on_refresh} disabled={*is_loading}>
                            {if *is_loading { "🔄 Loading..." } else { "🔄 Refresh" }}
                        </button>
                    </div>
                </div>

                <div class="filters-section">
                    <div class="filter-group">
                        <label for="type-filter">{"Event Type:"}</label>
                        <select id="type-filter" onchange={on_filter_type_change} value={(*filter_type).clone()}>
                            <option value="all">{"All Types"}</option>
                            {for unique_types.iter().map(|event_type| {
                                html! {
                                    <option value={event_type.clone()}>{event_type.clone()}</option>
                                }
                            })}
                        </select>
                    </div>
                    
                    <div class="filter-group">
                        <label for="server-filter">{"Server:"}</label>
                        <select id="server-filter" onchange={on_filter_server_change} value={(*filter_server).clone()}>
                            <option value="all">{"All Servers"}</option>
                            {for unique_servers.iter().map(|server| {
                                html! {
                                    <option value={server.clone()}>{server.clone()}</option>
                                }
                            })}
                        </select>
                    </div>

                    <div class="filter-stats">
                        <span>{"Showing "}{filtered_logs.len()}{" of "}{logs.len()}{" events"}</span>
                    </div>
                </div>

                <div class="logs-list">
                    {if *is_loading && logs.is_empty() {
                        html! {
                            <div class="loading-state">
                                <div class="loading-spinner"></div>
                                <p>{"Loading logs..."}</p>
                            </div>
                        }
                    } else if filtered_logs.is_empty() {
                        html! {
                            <div class="empty-state">
                                <h3>{"No logs found"}</h3>
                                <p>{"No log events match the current filters."}</p>
                            </div>
                        }
                    } else {
                        html! {
                            <div class="log-events">
                                {for filtered_logs.iter().map(|log| {
                                    let event_class = get_event_type_class(&log.event_type);
                                    let event_icon = get_event_type_icon(&log.event_type);
                                    
                                    html! {
                                        <div class={classes!("log-event", event_class)}>
                                            <div class="log-header">
                                                <div class="log-icon">{event_icon}</div>
                                                <div class="log-title">
                                                    <span class="log-type">{log.event_type.clone()}</span>
                                                    {if let Some(player_name) = &log.player_name {
                                                        html! { <span class="log-player">{" - Player: "}{player_name.clone()}</span> }
                                                    } else {
                                                        html! {}
                                                    }}
                                                    {if let Some(server_name) = &log.server_name {
                                                        html! { <span class="log-server">{" - Server: "}{server_name.clone()}</span> }
                                                    } else {
                                                        html! {}
                                                    }}
                                                </div>
                                                <div class="log-timestamp">{format_timestamp(&log.timestamp)}</div>
                                            </div>
                                            <div class="log-message">{log.message.clone()}</div>
                                            {if log.player_uuid.is_some() || log.metadata.is_some() {
                                                html! {
                                                    <details class="log-details">
                                                        <summary>{"Show details"}</summary>
                                                        <div class="log-details-content">
                                                            {if let Some(player_uuid) = &log.player_uuid {
                                                                html! {
                                                                    <div class="detail-item">
                                                                        <span class="detail-key">{"Player UUID: "}</span>
                                                                        <span class="detail-value">{player_uuid.clone()}</span>
                                                                    </div>
                                                                }
                                                            } else {
                                                                html! {}
                                                            }}
                                                            {if let Some(metadata) = &log.metadata {
                                                                html! {
                                                                    <div class="detail-item">
                                                                        <span class="detail-key">{"Metadata:"}</span>
                                                                        {render_metadata(metadata)}
                                                                    </div>
                                                                }
                                                            } else {
                                                                html! {}
                                                            }}
                                                        </div>
                                                    </details>
                                                }
                                            } else {
                                                html! {}
                                            }}
                                        </div>
                                    }
                                })}
                            </div>
                        }
                    }}
                </div>
            </div>
        </div>
    }
}