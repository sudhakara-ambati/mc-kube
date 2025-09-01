use yew::prelude::*;
use yew_router::prelude::*;
use crate::Route;

pub fn servers_view() -> Html {
    html! {
        <>
            <nav class="navbar">
                <Link<Route> to={Route::Dashboard} classes="nav-link">{"Dashboard"}</Link<Route>>
                <Link<Route> to={Route::Servers} classes="nav-link">{"Server List"}</Link<Route>>
            </nav>
            <div class="servers-page">
                <div class="page-header">
                    <h1>{"Server List"}</h1>
                    <div class="header-actions">
                        <button class="btn btn-secondary">{"🔄 Refresh List"}</button>
                        <button class="btn btn-success">{"➕ Add Server"}</button>
                    </div>
                </div>
                <div class="servers-grid">
                    <p>{"Server list will be rendered here"}</p>
                </div>
            </div>
        </>
    }
}
