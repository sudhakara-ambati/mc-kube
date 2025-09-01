use yew::prelude::*;
use gloo_net::http::Request;
use wasm_bindgen_futures::spawn_local;

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

    spawn_local(async move {
        let mut request_builder = Request::post(&url);
        if let Some(_body_data) = &body {
            request_builder = request_builder.header("Content-Type", "application/json");
        }

        let request = if let Some(body_data) = body {
            request_builder.body(body_data)
        } else {
            request_builder.body("")
        };

        let built_request = match request {
            Ok(req) => req,
            Err(e) => {
                callback.emit(Err(ApiError::Request(e.to_string())));
                return;
            }
        };

        match built_request.send().await {
            Ok(resp) => {
                if resp.ok() { // Check for 2xx status codes
                    match resp.text().await {
                        Ok(text) => callback.emit(Ok(text)),
                        Err(e) => callback.emit(Err(ApiError::Parse(e.to_string()))),
                    }
                } else {
                    let status = resp.status();
                    let status_text = resp.status_text();
                    callback.emit(Err(ApiError::Network(format!(
                        "HTTP Error: {} {}",
                        status, status_text
                    ))));
                }
            }
            Err(e) => {
                callback.emit(Err(ApiError::Network(e.to_string())));
            }
        }
    });
}
