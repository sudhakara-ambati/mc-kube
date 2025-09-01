use yew_router::prelude::Link;
use crate::Route;
use yew::prelude::*;

pub fn server_detail_view(server_id: &str) -> Html {
    html! {
        <>
            <nav class="navbar">
                <Link<Route> to={Route::Dashboard} classes="nav-link">{"Dashboard"}</Link<Route>>
                <Link<Route> to={Route::Servers} classes="nav-link">{"Server List"}</Link<Route>>
            </nav>
            <div class="server-detail-page">
                <div class="page-header">
                    <div class="header-left">
                        <h1>{format!("Server: {}", server_id)}</h1>
                        <p>{"Detailed metrics and controls"}</p>
                    </div>
                    <div class="header-right">
                        <span class="server-status online">{"● Online"}</span>
                    </div>
                </div>
                <div class="server-detail-grid">
                    <div class="card metrics-card">
                        <div class="card-header">
                            <h2>{"Server Metrics"}</h2>
                            <span class="auto-refresh">{"● Auto-refresh"}</span>
                        </div>
                        <div class="card-content">
                            <pre class="metrics-display">{"Loading metrics..."}</pre>
                        </div>
                    </div>
                    <div class="card logs-card">
                        <div class="card-header">
                            <h2>{"Server Logs"}</h2>
                            <button class="btn btn-refresh">{"🔄 Refresh"}</button>
                        </div>
                        <div class="card-content">
                            <pre class="logs-display">{"Loading logs..."}</pre>
                        </div>
                    </div>
                    <div class="card players-card">
                        <div class="card-header">
                            <h2>{"Online Players"}</h2>
                            <button class="btn btn-refresh">{"🔄 Refresh"}</button>
                        </div>
                        <div class="card-content">
                            <pre class="players-display">{"Loading players..."}</pre>
                        </div>
                    </div>
                    <div class="card controls-card">
                        <div class="card-header">
                            <h2>{"Server Controls"}</h2>
                        </div>
                        <div class="card-content">
                            <div class="control-sections">
                                <div class="control-section">
                                    <h3>{"Server Management"}</h3>
                                    <div class="button-row">
                                        <button class="btn btn-warning">{"🔄 Restart Server"}</button>
                                        <button class="btn btn-success">{"💾 Create Backup"}</button>
                                    </div>
                                </div>
                                <div class="control-section">
                                    <h3>{"Player Management"}</h3>
                                    <div class="button-row">
                                        <button class="btn btn-danger">{"👢 Kick Player"}</button>
                                        <button class="btn btn-danger">{"🚫 Ban Player"}</button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </>
    }
}
