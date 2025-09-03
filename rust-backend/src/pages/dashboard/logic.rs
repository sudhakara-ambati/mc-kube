use yew::{function_component, html, Html, use_state, use_effect_with, Callback, MouseEvent};
use crate::utils::{api_get, api_post, ApiError};
use gloo_timers::callback::Interval;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct ServerSummary {
    #[serde(rename = "totalServers")]
    pub total_servers: i32,
    #[serde(rename = "onlineServers")]
    pub online_servers: i32,
    #[serde(rename = "offlineServers")]
    pub offline_servers: i32,
    #[serde(rename = "healthyServers")]
    pub healthy_servers: i32,
    #[serde(rename = "totalPlayers")]
    pub total_players: i32,
}

impl Default for ServerSummary {
    fn default() -> Self {
        Self {
            total_servers: 0,
            online_servers: 0,
            offline_servers: 0,
            healthy_servers: 0,
            total_players: 0,
        }
    }
}

#[function_component(Dashboard)]
pub fn dashboard() -> Html {
    let server_summary = use_state(|| ServerSummary::default());
    let queue_count = use_state(|| 0i32);
    let network = use_state(|| String::from("Total Bandwidth: 100 Mbps\nCurrent Usage: 42.7 Mbps\nPackets/sec: 12,847\nConnections: 156\nLatency: 23ms\nPacket Loss: 0.02%"));
    let response = use_state(|| String::new());

    {
        let server_summary = server_summary.clone();
        let queue_count = queue_count.clone();
        use_effect_with((), move |_| {
            let server_summary = server_summary.clone();
            let queue_count = queue_count.clone();
            let interval = Interval::new(3000, move || {
                api_get("http://localhost:8080/server/summary", {
                    let server_summary = server_summary.clone();
                    Callback::from(move |data: String| {
                        if let Ok(summary) = serde_json::from_str::<ServerSummary>(&data) {
                            server_summary.set(summary);
                        }
                    })
                });
                
                api_get("http://localhost:8080/queue/count", {
                    let queue_count = queue_count.clone();
                    Callback::from(move |data: String| {
                        if let Ok(count) = data.trim().parse::<i32>() {
                            queue_count.set(count);
                        }
                    })
                });
            });
            move || drop(interval)
        });
    }

    let restart_all = {
        let response = response.clone();
        Callback::from(move |_: MouseEvent| {
            api_post("http://localhost:8080/servers/restart-all", None, {
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

    let backup_all = {
        let response = response.clone();
        Callback::from(move |_: MouseEvent| {
            api_post("http://localhost:8080/servers/backup-all", None, {
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

    let refresh_network = {
        let network = network.clone();
        Callback::from(move |_: MouseEvent| {
            api_get("http://localhost:8080/metrics/network", {
                let network = network.clone();
                Callback::from(move |data| network.set(data))
            });
        })
    };

    html! {
        <div class="dashboard-page">
            <div class="page-header">
                <div>
                    <h1>{"Dashboard"}</h1>
                    <p>{"Overall server network overview"}</p>
                </div>
            </div>
            <div class="dashboard-grid">
                <div class="card metrics-card">
                    <div class="card-header">
                        <h2>{"Overall Metrics"}</h2>
                        <span class="auto-refresh">{"● Auto-refresh"}</span>
                    </div>
                    <div class="card-content">
                        <div class="metrics-display">
                            <div class="metric-row">
                                <span class="metric-label">{"Total Servers:"}</span>
                                <span class="metric-value">{server_summary.total_servers}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Online Servers:"}</span>
                                <span class="metric-value">{server_summary.online_servers}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Offline Servers:"}</span>
                                <span class="metric-value">{server_summary.offline_servers}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Healthy Servers:"}</span>
                                <span class="metric-value">{server_summary.healthy_servers}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Total Players:"}</span>
                                <span class="metric-value">{server_summary.total_players}</span>
                            </div>
                            <div class="metric-row">
                                <span class="metric-label">{"Players in Queue:"}</span>
                                <span class="metric-value">{*queue_count}</span>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="card network-card">
                    <div class="card-header">
                        <h2>{"Network Stats"}</h2>
                        <button class="btn btn-refresh" onclick={refresh_network}>{"🔄 Refresh"}</button>
                    </div>
                    <div class="card-content">
                        <pre class="metrics-display">{&*network}</pre>
                    </div>
                </div>
                <div class="card controls-card">
                    <div class="card-header">
                        <h2>{"Global Controls"}</h2>
                    </div>
                    <div class="card-content">
                        <div class="button-grid">
                            <button class="btn btn-danger btn-large" onclick={restart_all}>{"🔄 Restart All Servers"}</button>
                            <button class="btn btn-success btn-large" onclick={backup_all}>{"💾 Backup All Servers"}</button>
                        </div>
                        {if !response.is_empty() {
                            html! {
                                <div class="response-box">
                                    <h3>{"Response:"}</h3>
                                    <pre>{&*response}</pre>
                                </div>
                            }
                        } else {
                            html! {}
                        }}
                    </div>
                </div>
            </div>
        </div>
    }
}
