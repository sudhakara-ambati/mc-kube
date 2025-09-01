use yew::prelude::*;
use yew_router::prelude::*;
use crate::Route;

pub fn dashboard_view() -> Html {
    html! {
        <>
            <nav class="navbar">
                <Link<Route> to={Route::Dashboard} classes="nav-link">{"Dashboard"}</Link<Route>>
                <Link<Route> to={Route::Servers} classes="nav-link">{"Server List"}</Link<Route>>
            </nav>
            <div class="dashboard-page">
                <div class="page-header">
                    <h1>{"Dashboard"}</h1>
                    <p>{"Overall server network overview"}</p>
                </div>
                <div class="dashboard-grid">
                    <div class="card metrics-card">
                        <div class="card-header">
                            <h2>{"Overall Metrics"}</h2>
                            <span class="auto-refresh">{"● Auto-refresh"}</span>
                        </div>
                        <div class="card-content">
                            <pre class="metrics-display">{"Loading metrics..."}</pre>
                        </div>
                    </div>
                    <div class="card network-card">
                        <div class="card-header">
                            <h2>{"Network Stats"}</h2>
                            <button class="btn btn-refresh">{"🔄 Refresh"}</button>
                        </div>
                        <div class="card-content">
                            <pre class="metrics-display">{"Loading network stats..."}</pre>
                        </div>
                    </div>
                    <div class="card controls-card">
                        <div class="card-header">
                            <h2>{"Global Controls"}</h2>
                        </div>
                        <div class="card-content">
                            <div class="button-grid">
                                <button class="btn btn-danger btn-large">{"🔄 Restart All Servers"}</button>
                                <button class="btn btn-success btn-large">{"💾 Backup All Servers"}</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </>
    }
}
