mod utils;
mod pages;
use yew::prelude::*;
use yew_router::prelude::*;
use pages::{Dashboard, Servers, ServerDetail};
#[derive(Clone, Routable, PartialEq)]
pub enum Route {
    #[at("/")]
    Dashboard,
    #[at("/servers")]
    Servers,
    #[at("/server/:id")]
    ServerDetail { id: String },
}
fn switch(routes: Route) -> Html {
    match routes {
        Route::Dashboard => html! { <Dashboard /> },
        Route::Servers => html! { <Servers /> },
        Route::ServerDetail { id } => html! { <ServerDetail server_id={id} /> },
    }
}
#[function_component(Nav)]
fn nav() -> Html {
    html! {
        <nav class="navbar">
            <Link<Route> to={Route::Dashboard} classes="nav-link">{"Dashboard"}</Link<Route>>
            <Link<Route> to={Route::Servers} classes="nav-link">{"Servers"}</Link<Route>>
        </nav>
    }
}
#[function_component(App)]
fn app() -> Html {
    html! {
        <BrowserRouter>
            <Nav />
            <Switch<Route> render={switch} />
        </BrowserRouter>
    }
}
fn main() {
    yew::Renderer::<App>::new().render();
}
