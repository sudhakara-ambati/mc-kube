use yew::prelude::*;
use gloo_net::http::Request;
use wasm_bindgen_futures::spawn_local;
use gloo_console::log;

#[derive(Clone, Debug, PartialEq)]
pub enum ApiError {
    Network(String),
    Parse(String),
    Request(String),
}

impl std::fmt::Display for ApiError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ApiError::Network(s) => write!(f, "Network Error: {}", s),
            ApiError::Parse(s) => write!(f, "Parsing Error: {}", s),
            ApiError::Request(s) => write!(f, "Request Error: {}", s),
        }
    }
}

pub fn api_get(url: &str, callback: Callback<String>) {
    let url = url.to_string();
    spawn_local(async move {
        if let Ok(resp) = Request::get(&url).send().await {
            if let Ok(text) = resp.text().await {
                callback.emit(text);
            }
        }
    });
}

pub fn api_post(url: &str, body: Option<&str>, callback: Callback<Result<String, ApiError>>) {
    let url = url.to_string();
    let body = body.map(|s| s.to_string());

    log!("=== API_POST CALLED ===");
    log!("URL:", url.clone());
    log!("Body:", body.clone().unwrap_or("None".to_string()));

    spawn_local(async move {
        let request_result = if let Some(body_data) = &body {
            Request::post(&url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(body_data)
        } else {
            Ok(Request::post(&url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()
                .unwrap())
        };

        match request_result {
            Ok(request) => {
                match request.send().await {
                    Ok(resp) => {
                        log!("Response status:", resp.status());
                        log!("Response headers:", format!("{:?}", resp.headers()));
                        if resp.ok() {
                            match resp.text().await {
                                Ok(text) => {
                                    log!("Response text:", text.clone());
                                    callback.emit(Ok(text));
                                }
                                Err(e) => {
                                    log!("Error reading response text:", e.to_string());
                                    callback.emit(Err(ApiError::Parse(format!("Failed to read response: {}", e))));
                                }
                            }
                        } else {
                            log!("HTTP Error status:", resp.status());
                            match resp.text().await {
                                Ok(error_text) => {
                                    log!("Error response body:", error_text.clone());
                                    callback.emit(Err(ApiError::Request(format!("HTTP Error: {} - {}", resp.status(), error_text))));
                                }
                                Err(_) => {
                                    callback.emit(Err(ApiError::Request(format!("HTTP Error: {}", resp.status()))));
                                }
                            }
                        }
                    }
                    Err(e) => {
                        log!("Network error:", e.to_string());
                        callback.emit(Err(ApiError::Network(format!("Network Error: {}", e))));
                    }
                }
            }
            Err(e) => {
                log!("Request build error:", e.to_string());
                callback.emit(Err(ApiError::Request(format!("Failed to build request: {}", e))));
            }
        }
    });
}
