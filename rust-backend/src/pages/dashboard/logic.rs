use yew::{function_component, html, Html, use_state, use_effect_with, Callback, MouseEvent};
use crate::utils::{api_get, api_post, ApiError};
use gloo_timers::callback::Interval;

#[function_component(Dashboard)]
pub fn dashboard() -> Html {
    let metrics = use_state(|| String::from("CPU: 45%\nRAM: 12.3/32 GB (38%)\nDisk: 145/500 GB (29%)\nNetwork: 24.5 MB/s ↑ 18.2 MB/s ↓\nUptime: 7d 14h 32m\nServers Online: 3/4"));
    let network = use_state(|| String::from("Total Bandwidth: 100 Mbps\nCurrent Usage: 42.7 Mbps\nPackets/sec: 12,847\nConnections: 156\nLatency: 23ms\nPacket Loss: 0.02%"));
    let response = use_state(|| String::new());

    {
        let metrics = metrics.clone();
        use_effect_with((), move |_| {
            let metrics = metrics.clone();
            let interval = Interval::new(3000, move || {
                api_get("http://localhost:8080/metrics/overall", {
                    let metrics = metrics.clone();
                    Callback::from(move |data| metrics.set(data))
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
                        <pre class="metrics-display">{&*metrics}</pre>
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
