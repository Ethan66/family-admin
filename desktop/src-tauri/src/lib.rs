pub mod commands;
pub mod index_store;
pub mod recycle_bin;
pub mod scanner;

#[cfg(test)]
#[path = "../../src/test/index_store_tests.rs"]
mod index_store_tests;
#[cfg(test)]
#[path = "../../src/test/scanner_tests.rs"]
mod scanner_tests;

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .invoke_handler(tauri::generate_handler![
            commands::load_resource_index,
            commands::list_photo_root_json_files,
            commands::scan_resources,
            commands::save_resource_metadata,
            commands::recycle_photo,
            commands::recycle_photo_directory,
            commands::create_photo_subfolder,
            commands::move_photos,
            commands::read_text_preview,
            commands::read_binary_preview,
            commands::open_with_default_app,
            commands::reveal_in_explorer,
        ])
        .run(tauri::generate_context!())
        .expect("failed to run Family Admin desktop application");
}
