use gloo_console::log;
use yew::prelude::*;
use yew_router::prelude::*;
use serde::Deserialize;
use crate::utils::{api_get, api_post, ApiError};
use crate::Route;

#[derive(Deserialize, Clone, PartialEq, Default)]
#[allow(dead_code)]
pub struct ServerInfo {
    pub servername: String,
    pub serverip: String,
    pub status: String,
    pub players: String,
    pub id: String,
}

#[function_component(Servers)]
pub fn servers() -> Html {
    let response = use_state(|| String::new());
    let show_add_modal = use_state(|| false);
    let new_server_id = use_state(|| String::new());
    let new_server_name = use_state(|| String::new());
    let new_server_ip = use_state(|| String::new());
    let navigator = use_navigator().unwrap();
    let servers = use_state(Vec::new);

    let fetch_servers = {
        let servers = servers.clone();
        Callback::from(move |_: ()| {
            api_get("/api/servers", {
                let servers = servers.clone();
                Callback::from(move |data: String| {
                    match serde_json::from_str::<Vec<ServerInfo>>(&data) {
                        Ok(server_list) => {
                            servers.set(server_list);
                        }
                        Err(e) => {
                            log!("Error parsing server list:", e.to_string());
                        }
                    }
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

    let _create_restart_handler = {
        let response = response.clone();
        Callback::from(move |servername: String| {
            api_post(&format!("http://localhost:8080/servers/{}/restart", servername), None, {
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

    let add_server_click = {
        let show_add_modal = show_add_modal.clone();
        Callback::from(move |_: MouseEvent| {
            show_add_modal.set(true);
        })
    };

    let close_modal = {
        let show_add_modal = show_add_modal.clone();
        let new_server_id = new_server_id.clone();
        let new_server_name = new_server_name.clone();
        let new_server_ip = new_server_ip.clone();
        Callback::from(move |_: MouseEvent| {
            show_add_modal.set(false);
            new_server_id.set(String::new());
            new_server_name.set(String::new());
            new_server_ip.set(String::new());
        })
    };

    let submit_server = {
        let response = response.clone();
        let show_add_modal = show_add_modal.clone();
        let new_server_id = new_server_id.clone();
        let new_server_name = new_server_name.clone();
        let new_server_ip = new_server_ip.clone();
        let fetch_servers = fetch_servers.clone();

        Callback::from(move |_: MouseEvent| {
            let server_data = format!(
                r#"{{"id":"{}","name":"{}","ip":"{}"}}"#,
                *new_server_id, *new_server_name, *new_server_ip
            );

            api_post("/servers/add", Some(&server_data), {
                let response = response.clone();
                let fetch_servers = fetch_servers.clone();
                Callback::from(move |result: Result<String, ApiError>| {
                    match result {
                        Ok(data) => {
                            response.set(format!("Server added successfully: {}", data));
                            fetch_servers.emit(());
                        }
                        Err(e) => {
                            response.set(format!("Error adding server: {}", e));
                        }
                    }
                })
            });

            show_add_modal.set(false);
            new_server_id.set(String::new());
            new_server_name.set(String::new());
            new_server_ip.set(String::new());
        })
    };

    html! { 
        <div class="servers-page">
            <div class="page-header">
                <div>
                    <h1>{"Server List"}</h1>
                    <p>{"Manage your Minecraft servers"}</p>
                </div>
                <div class="header-actions">
                    <button class="btn btn-success" onclick={add_server_click}>{"➕ Add Server"}</button>
                </div>
            </div>
            <div class="servers-grid">
                {for servers.iter().map(|server| {
                    let restart_handler = {
                        let response = response.clone();
                        let server_name = server.servername.clone();
                        Callback::from(move |_: MouseEvent| {
                            response.set(format!("Restarting server: {}", server_name));
                        })
                    };
                    let stop_handler = {
                        let response = response.clone();
                        let server_name = server.servername.clone();
                        Callback::from(move |_: MouseEvent| {
                            response.set(format!("Stopping server: {}", server_name));
                        })
                    };
                    html! {
                        <div class="server-card" key={server.id.clone()}>
                            <div class="server-header">
                                <h3 class="server-name">{&server.servername}</h3>
                                <span class={format!("server-status {}", if server.status == "Online" { "online" } else { "offline" })}>{&server.status}</span>
                            </div>
                            <div class="server-info">
                                <div class="info-row">
                                    <span class="label">{"Players:"}</span>
                                    <span class="value">{&server.players}</span>
                                </div>
                                <div class="info-row">
                                    <span class="label">{"IP:"}</span>
                                    <span class="value">{&server.serverip}</span>
                                </div>
                                <div class="info-row">
                                    <span class="label">{"ID:"}</span>
                                    <span class="value">{&server.id}</span>
                                </div>
                            </div>
                            <div class="server-actions">
                                <button class="btn btn-sm btn-primary" onclick={{
                                    let server_id = server.id.clone();
                                    let navigator = navigator.clone();
                                    Callback::from(move |_: MouseEvent| {
                                        navigator.push(&Route::ServerDetail { id: server_id.clone() });
                                    })
                                }}>{"📊 Details"}</button>
                                <button class="btn btn-sm btn-warning" onclick={restart_handler}>{"🔄 Restart"}</button>
                                <button class="btn btn-sm btn-danger" onclick={stop_handler}>{"⏹ Stop"}</button>
                            </div>
                        </div>
                    }
                })}
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
                                    <label>{"Server ID"}</label>
                                    <input 
                                        type="text" 
                                        placeholder="e.g., survival-2" 
                                        value={(*new_server_id).clone()}
                                        oninput={{
                                            let new_server_id = new_server_id.clone();
                                            Callback::from(move |e: InputEvent| {
                                                let input: web_sys::HtmlInputElement = e.target_unchecked_into();
                                                new_server_id.set(input.value());
                                            })
                                        }}
                                    />
                                </div>
                                <div class="form-group">
                                    <label>{"Server Name"}</label>
                                    <input 
                                        type="text" 
                                        placeholder="e.g., Survival World 2" 
                                        value={(*new_server_name).clone()}
                                        oninput={{
                                            let new_server_name = new_server_name.clone();
                                            Callback::from(move |e: InputEvent| {
                                                let input: web_sys::HtmlInputElement = e.target_unchecked_into();
                                                new_server_name.set(input.value());
                                            })
                                        }}
                                    />
                                </div>
                                <div class="form-group">
                                    <label>{"Server IP"}</label>
                                    <input 
                                        type="text" 
                                        placeholder="e.g., 192.168.1.104:25565" 
                                        value={(*new_server_ip).clone()}
                                        oninput={{
                                            let new_server_ip = new_server_ip.clone();
                                            Callback::from(move |e: InputEvent| {
                                                let input: web_sys::HtmlInputElement = e.target_unchecked_into();
                                                new_server_ip.set(input.value());
                                            })
                                        }}
                                    />
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
