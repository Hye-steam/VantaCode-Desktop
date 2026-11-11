/*
 * Generated from the Android resources of AiCode/VantaCode (values/strings.xml zh,
 * values-en/strings.xml en). Do not edit by hand — regenerate with
 * tooling/genstrings.js. License: GPL-3.0, see LICENSE.
 */
package com.vantacode.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

object Strings {
    /** "zh" or "en". Update on language change; read from composables for recomposition. */
    var current by mutableStateOf("zh")

    private fun catalog(): Map<String, String> = if (current == "en") en else zh

    fun formatted(template: String, vararg args: Any?): String =
        if (args.isEmpty()) template else String.format(Locale.ROOT, template, *args)

    val about_auto_check_update: String get() = catalog().getValue("about_auto_check_update")
    val about_auto_check_update_desc: String get() = catalog().getValue("about_auto_check_update_desc")
    val about_check_failed: String get() = catalog().getValue("about_check_failed")
    val about_check_update: String get() = catalog().getValue("about_check_update")
    val about_checking_update: String get() = catalog().getValue("about_checking_update")
    val about_description: String get() = catalog().getValue("about_description")
    val about_download: String get() = catalog().getValue("about_download")
    val about_github_repo: String get() = catalog().getValue("about_github_repo")
    val about_later: String get() = catalog().getValue("about_later")
    val about_license: String get() = catalog().getValue("about_license")
    val about_network_error: String get() = catalog().getValue("about_network_error")
    val about_new_version_found: String get() = catalog().getValue("about_new_version_found")
    val about_no_changelog: String get() = catalog().getValue("about_no_changelog")
    val about_ok: String get() = catalog().getValue("about_ok")
    val about_parse_version_failed: String get() = catalog().getValue("about_parse_version_failed")
    val about_up_to_date: String get() = catalog().getValue("about_up_to_date")
    val about_up_to_date_detail: String get() = catalog().getValue("about_up_to_date_detail")
    val about_update_apk_ready: String get() = catalog().getValue("about_update_apk_ready")
    val about_update_background: String get() = catalog().getValue("about_update_background")
    val about_update_browser: String get() = catalog().getValue("about_update_browser")
    val about_update_channel: String get() = catalog().getValue("about_update_channel")
    val about_update_download_failed: String get() = catalog().getValue("about_update_download_failed")
    val about_update_download_failed_hint: String get() = catalog().getValue("about_update_download_failed_hint")
    val about_update_download_install: String get() = catalog().getValue("about_update_download_install")
    val about_update_download_size: String get() = catalog().getValue("about_update_download_size")
    val about_update_downloading: String get() = catalog().getValue("about_update_downloading")
    val about_update_install: String get() = catalog().getValue("about_update_install")
    val about_update_install_failed: String get() = catalog().getValue("about_update_install_failed")
    val about_update_mirror: String get() = catalog().getValue("about_update_mirror")
    val about_update_mirror_default: String get() = catalog().getValue("about_update_mirror_default")
    val about_update_mirror_desc: String get() = catalog().getValue("about_update_mirror_desc")
    val about_update_mirror_invalid: String get() = catalog().getValue("about_update_mirror_invalid")
    val about_update_mirror_preset_cdn: String get() = catalog().getValue("about_update_mirror_preset_cdn")
    val about_update_mirror_preset_direct: String get() = catalog().getValue("about_update_mirror_preset_direct")
    val about_update_mirror_preset_v6: String get() = catalog().getValue("about_update_mirror_preset_v6")
    val about_update_need_permission: String get() = catalog().getValue("about_update_need_permission")
    val about_update_retry: String get() = catalog().getValue("about_update_retry")
    val about_update_verify_not_apk: String get() = catalog().getValue("about_update_verify_not_apk")
    val about_update_verify_not_newer: String get() = catalog().getValue("about_update_verify_not_newer")
    val about_update_verify_package: String get() = catalog().getValue("about_update_verify_package")
    val about_update_verify_signature: String get() = catalog().getValue("about_update_verify_signature")
    val about_version: String get() = catalog().getValue("about_version")
    val agent_compaction_failed: String get() = catalog().getValue("agent_compaction_failed")
    val agent_complete_notification_background_body: String get() = catalog().getValue("agent_complete_notification_background_body")
    val agent_complete_notification_background_multi_body: String get() = catalog().getValue("agent_complete_notification_background_multi_body")
    val agent_complete_notification_body: String get() = catalog().getValue("agent_complete_notification_body")
    val agent_complete_notification_title: String get() = catalog().getValue("agent_complete_notification_title")
    val agent_context_no_compaction: String get() = catalog().getValue("agent_context_no_compaction")
    val agent_input_budget_exceeded: String get() = catalog().getValue("agent_input_budget_exceeded")
    val agent_model_not_selected: String get() = catalog().getValue("agent_model_not_selected")
    val agent_stop_context_exceeded: String get() = catalog().getValue("agent_stop_context_exceeded")
    val agent_stop_refusal: String get() = catalog().getValue("agent_stop_refusal")
    val agent_stopped_by_user: String get() = catalog().getValue("agent_stopped_by_user")
    val agent_tool_executing: String get() = catalog().getValue("agent_tool_executing")
    val agreement_accept: String get() = catalog().getValue("agreement_accept")
    val agreement_decline: String get() = catalog().getValue("agreement_decline")
    val agreement_notice_conjunction: String get() = catalog().getValue("agreement_notice_conjunction")
    val agreement_notice_prefix: String get() = catalog().getValue("agreement_notice_prefix")
    val agreement_notice_suffix: String get() = catalog().getValue("agreement_notice_suffix")
    val agreement_privacy: String get() = catalog().getValue("agreement_privacy")
    val agreement_privacy_link: String get() = catalog().getValue("agreement_privacy_link")
    val agreement_privacy_url: String get() = catalog().getValue("agreement_privacy_url")
    val agreement_terms: String get() = catalog().getValue("agreement_terms")
    val agreement_terms_link: String get() = catalog().getValue("agreement_terms_link")
    val agreement_terms_url: String get() = catalog().getValue("agreement_terms_url")
    val agreement_title: String get() = catalog().getValue("agreement_title")
    val app_name: String get() = catalog().getValue("app_name")
    val ask_confirm: String get() = catalog().getValue("ask_confirm")
    val ask_confirm_title: String get() = catalog().getValue("ask_confirm_title")
    val ask_custom_answer: String get() = catalog().getValue("ask_custom_answer")
    val ask_input_hint: String get() = catalog().getValue("ask_input_hint")
    val ask_supplement: String get() = catalog().getValue("ask_supplement")
    val backup_actions: String get() = catalog().getValue("backup_actions")
    val backup_data_app_settings: String get() = catalog().getValue("backup_data_app_settings")
    val backup_data_chat_history: String get() = catalog().getValue("backup_data_chat_history")
    val backup_data_mcp: String get() = catalog().getValue("backup_data_mcp")
    val backup_data_permissions: String get() = catalog().getValue("backup_data_permissions")
    val backup_data_remote: String get() = catalog().getValue("backup_data_remote")
    val backup_data_workspace: String get() = catalog().getValue("backup_data_workspace")
    val backup_export_btn: String get() = catalog().getValue("backup_export_btn")
    val backup_export_failed: String get() = catalog().getValue("backup_export_failed")
    val backup_export_subtitle: String get() = catalog().getValue("backup_export_subtitle")
    val backup_export_title: String get() = catalog().getValue("backup_export_title")
    val backup_exported: String get() = catalog().getValue("backup_exported")
    val backup_import_btn: String get() = catalog().getValue("backup_import_btn")
    val backup_import_done: String get() = catalog().getValue("backup_import_done")
    val backup_import_failed: String get() = catalog().getValue("backup_import_failed")
    val backup_import_subtitle: String get() = catalog().getValue("backup_import_subtitle")
    val backup_import_title: String get() = catalog().getValue("backup_import_title")
    val backup_net_access_key: String get() = catalog().getValue("backup_net_access_key")
    val backup_net_add_target: String get() = catalog().getValue("backup_net_add_target")
    val backup_net_base_path: String get() = catalog().getValue("backup_net_base_path")
    val backup_net_base_path_hint: String get() = catalog().getValue("backup_net_base_path_hint")
    val backup_net_bucket: String get() = catalog().getValue("backup_net_bucket")
    val backup_net_delete_failed: String get() = catalog().getValue("backup_net_delete_failed")
    val backup_net_delete_target_confirm: String get() = catalog().getValue("backup_net_delete_target_confirm")
    val backup_net_edit_target: String get() = catalog().getValue("backup_net_edit_target")
    val backup_net_endpoint: String get() = catalog().getValue("backup_net_endpoint")
    val backup_net_endpoint_hint: String get() = catalog().getValue("backup_net_endpoint_hint")
    val backup_net_list_failed: String get() = catalog().getValue("backup_net_list_failed")
    val backup_net_loading: String get() = catalog().getValue("backup_net_loading")
    val backup_net_name: String get() = catalog().getValue("backup_net_name")
    val backup_net_no_target: String get() = catalog().getValue("backup_net_no_target")
    val backup_net_no_target_hint: String get() = catalog().getValue("backup_net_no_target_hint")
    val backup_net_password: String get() = catalog().getValue("backup_net_password")
    val backup_net_path_style: String get() = catalog().getValue("backup_net_path_style")
    val backup_net_region: String get() = catalog().getValue("backup_net_region")
    val backup_net_remote_delete: String get() = catalog().getValue("backup_net_remote_delete")
    val backup_net_remote_delete_confirm: String get() = catalog().getValue("backup_net_remote_delete_confirm")
    val backup_net_remote_empty: String get() = catalog().getValue("backup_net_remote_empty")
    val backup_net_remote_list_title: String get() = catalog().getValue("backup_net_remote_list_title")
    val backup_net_remote_restore: String get() = catalog().getValue("backup_net_remote_restore")
    val backup_net_restore: String get() = catalog().getValue("backup_net_restore")
    val backup_net_restore_subtitle: String get() = catalog().getValue("backup_net_restore_subtitle")
    val backup_net_secret_key: String get() = catalog().getValue("backup_net_secret_key")
    val backup_net_section: String get() = catalog().getValue("backup_net_section")
    val backup_net_session_token: String get() = catalog().getValue("backup_net_session_token")
    val backup_net_targets: String get() = catalog().getValue("backup_net_targets")
    val backup_net_test_connection: String get() = catalog().getValue("backup_net_test_connection")
    val backup_net_test_failed: String get() = catalog().getValue("backup_net_test_failed")
    val backup_net_test_success: String get() = catalog().getValue("backup_net_test_success")
    val backup_net_testing: String get() = catalog().getValue("backup_net_testing")
    val backup_net_type_s3: String get() = catalog().getValue("backup_net_type_s3")
    val backup_net_type_webdav: String get() = catalog().getValue("backup_net_type_webdav")
    val backup_net_upload: String get() = catalog().getValue("backup_net_upload")
    val backup_net_upload_done: String get() = catalog().getValue("backup_net_upload_done")
    val backup_net_upload_failed: String get() = catalog().getValue("backup_net_upload_failed")
    val backup_net_upload_password_hint: String get() = catalog().getValue("backup_net_upload_password_hint")
    val backup_net_upload_password_required: String get() = catalog().getValue("backup_net_upload_password_required")
    val backup_net_upload_short: String get() = catalog().getValue("backup_net_upload_short")
    val backup_net_upload_subtitle: String get() = catalog().getValue("backup_net_upload_subtitle")
    val backup_net_uploading: String get() = catalog().getValue("backup_net_uploading")
    val backup_net_username: String get() = catalog().getValue("backup_net_username")
    val backup_operation_failed: String get() = catalog().getValue("backup_operation_failed")
    val backup_password_hint: String get() = catalog().getValue("backup_password_hint")
    val backup_password_input: String get() = catalog().getValue("backup_password_input")
    val backup_password_label: String get() = catalog().getValue("backup_password_label")
    val backup_password_optional_hint: String get() = catalog().getValue("backup_password_optional_hint")
    val backup_processing: String get() = catalog().getValue("backup_processing")
    val backup_processing_data: String get() = catalog().getValue("backup_processing_data")
    val backup_read_failed: String get() = catalog().getValue("backup_read_failed")
    val backup_restore_workspaces_confirm: String get() = catalog().getValue("backup_restore_workspaces_confirm")
    val backup_restore_workspaces_files: String get() = catalog().getValue("backup_restore_workspaces_files")
    val backup_restore_workspaces_select_all: String get() = catalog().getValue("backup_restore_workspaces_select_all")
    val backup_restore_workspaces_title: String get() = catalog().getValue("backup_restore_workspaces_title")
    val backup_restore_workspaces_warning: String get() = catalog().getValue("backup_restore_workspaces_warning")
    val backup_restored_data: String get() = catalog().getValue("backup_restored_data")
    val backup_select_data: String get() = catalog().getValue("backup_select_data")
    val backup_set_password: String get() = catalog().getValue("backup_set_password")
    val backup_settings_covered: String get() = catalog().getValue("backup_settings_covered")
    val backup_stat_chat_messages: String get() = catalog().getValue("backup_stat_chat_messages")
    val backup_stat_chat_sessions: String get() = catalog().getValue("backup_stat_chat_sessions")
    val backup_stat_mcp_servers: String get() = catalog().getValue("backup_stat_mcp_servers")
    val backup_stat_permission_rules: String get() = catalog().getValue("backup_stat_permission_rules")
    val backup_stat_providers: String get() = catalog().getValue("backup_stat_providers")
    val backup_stat_remote_connections: String get() = catalog().getValue("backup_stat_remote_connections")
    val backup_stat_remote_mounts: String get() = catalog().getValue("backup_stat_remote_mounts")
    val backup_stat_todo_items: String get() = catalog().getValue("backup_stat_todo_items")
    val backup_stat_workspace_files: String get() = catalog().getValue("backup_stat_workspace_files")
    val backup_tab_local: String get() = catalog().getValue("backup_tab_local")
    val backup_workspace_git_hint: String get() = catalog().getValue("backup_workspace_git_hint")
    val backup_write_failed: String get() = catalog().getValue("backup_write_failed")
    val backup_wrong_password: String get() = catalog().getValue("backup_wrong_password")
    val browser_address_hint: String get() = catalog().getValue("browser_address_hint")
    val browser_back: String get() = catalog().getValue("browser_back")
    val browser_close_tab: String get() = catalog().getValue("browser_close_tab")
    val browser_devtools: String get() = catalog().getValue("browser_devtools")
    val browser_devtools_desc: String get() = catalog().getValue("browser_devtools_desc")
    val browser_devtools_title: String get() = catalog().getValue("browser_devtools_title")
    val browser_dialog_alert_title: String get() = catalog().getValue("browser_dialog_alert_title")
    val browser_dialog_confirm_title: String get() = catalog().getValue("browser_dialog_confirm_title")
    val browser_dialog_prompt_title: String get() = catalog().getValue("browser_dialog_prompt_title")
    val browser_empty_click_hint: String get() = catalog().getValue("browser_empty_click_hint")
    val browser_empty_hint: String get() = catalog().getValue("browser_empty_hint")
    val browser_forward: String get() = catalog().getValue("browser_forward")
    val browser_menu: String get() = catalog().getValue("browser_menu")
    val browser_menu_general_section: String get() = catalog().getValue("browser_menu_general_section")
    val browser_menu_title: String get() = catalog().getValue("browser_menu_title")
    val browser_new_tab: String get() = catalog().getValue("browser_new_tab")
    val browser_night_mode_desc: String get() = catalog().getValue("browser_night_mode_desc")
    val browser_night_mode_off: String get() = catalog().getValue("browser_night_mode_off")
    val browser_night_mode_on: String get() = catalog().getValue("browser_night_mode_on")
    val browser_night_mode_title: String get() = catalog().getValue("browser_night_mode_title")
    val browser_not_ready: String get() = catalog().getValue("browser_not_ready")
    val browser_reload: String get() = catalog().getValue("browser_reload")
    val browser_tab_default_title: String get() = catalog().getValue("browser_tab_default_title")
    val browser_tabs_count: String get() = catalog().getValue("browser_tabs_count")
    val browser_tabs_manage: String get() = catalog().getValue("browser_tabs_manage")
    val browser_title: String get() = catalog().getValue("browser_title")
    val browser_ua_android_phone: String get() = catalog().getValue("browser_ua_android_phone")
    val browser_ua_android_phone_desc: String get() = catalog().getValue("browser_ua_android_phone_desc")
    val browser_ua_android_tablet: String get() = catalog().getValue("browser_ua_android_tablet")
    val browser_ua_android_tablet_desc: String get() = catalog().getValue("browser_ua_android_tablet_desc")
    val browser_ua_applied_toast: String get() = catalog().getValue("browser_ua_applied_toast")
    val browser_ua_custom: String get() = catalog().getValue("browser_ua_custom")
    val browser_ua_custom_desc: String get() = catalog().getValue("browser_ua_custom_desc")
    val browser_ua_custom_dialog_title: String get() = catalog().getValue("browser_ua_custom_dialog_title")
    val browser_ua_custom_input_hint: String get() = catalog().getValue("browser_ua_custom_input_hint")
    val browser_ua_default: String get() = catalog().getValue("browser_ua_default")
    val browser_ua_default_desc: String get() = catalog().getValue("browser_ua_default_desc")
    val browser_ua_desc: String get() = catalog().getValue("browser_ua_desc")
    val browser_ua_desktop_pc: String get() = catalog().getValue("browser_ua_desktop_pc")
    val browser_ua_desktop_pc_desc: String get() = catalog().getValue("browser_ua_desktop_pc_desc")
    val browser_ua_ipad: String get() = catalog().getValue("browser_ua_ipad")
    val browser_ua_ipad_desc: String get() = catalog().getValue("browser_ua_ipad_desc")
    val browser_ua_iphone: String get() = catalog().getValue("browser_ua_iphone")
    val browser_ua_iphone_desc: String get() = catalog().getValue("browser_ua_iphone_desc")
    val browser_ua_section: String get() = catalog().getValue("browser_ua_section")
    val chat_action_copy: String get() = catalog().getValue("chat_action_copy")
    val chat_action_delete: String get() = catalog().getValue("chat_action_delete")
    val chat_action_edit: String get() = catalog().getValue("chat_action_edit")
    val chat_action_fork: String get() = catalog().getValue("chat_action_fork")
    val chat_add_attachment: String get() = catalog().getValue("chat_add_attachment")
    val chat_approve_and_implement: String get() = catalog().getValue("chat_approve_and_implement")
    val chat_attachment_max: String get() = catalog().getValue("chat_attachment_max")
    val chat_attachment_open: String get() = catalog().getValue("chat_attachment_open")
    val chat_attachment_prefix: String get() = catalog().getValue("chat_attachment_prefix")
    val chat_attachment_preview: String get() = catalog().getValue("chat_attachment_preview")
    val chat_attachment_uploaded: String get() = catalog().getValue("chat_attachment_uploaded")
    val chat_attachment_uploaded_partial: String get() = catalog().getValue("chat_attachment_uploaded_partial")
    val chat_attachment_uploading: String get() = catalog().getValue("chat_attachment_uploading")
    val chat_batch_delete_confirm: String get() = catalog().getValue("chat_batch_delete_confirm")
    val chat_batch_delete_title: String get() = catalog().getValue("chat_batch_delete_title")
    val chat_batch_select: String get() = catalog().getValue("chat_batch_select")
    val chat_bg_command_done: String get() = catalog().getValue("chat_bg_command_done")
    val chat_bg_commands_done: String get() = catalog().getValue("chat_bg_commands_done")
    val chat_bg_commands_partial_failed: String get() = catalog().getValue("chat_bg_commands_partial_failed")
    val chat_cache_hit_rate: String get() = catalog().getValue("chat_cache_hit_rate")
    val chat_camera_file_failed: String get() = catalog().getValue("chat_camera_file_failed")
    val chat_compaction_failed: String get() = catalog().getValue("chat_compaction_failed")
    val chat_compressing_context: String get() = catalog().getValue("chat_compressing_context")
    val chat_connecting_remote: String get() = catalog().getValue("chat_connecting_remote")
    val chat_context_compressed: String get() = catalog().getValue("chat_context_compressed")
    val chat_context_usage: String get() = catalog().getValue("chat_context_usage")
    val chat_context_usage_estimated: String get() = catalog().getValue("chat_context_usage_estimated")
    val chat_continue_feedback: String get() = catalog().getValue("chat_continue_feedback")
    val chat_copied: String get() = catalog().getValue("chat_copied")
    val chat_copy: String get() = catalog().getValue("chat_copy")
    val chat_delete_session: String get() = catalog().getValue("chat_delete_session")
    val chat_delete_session_confirm: String get() = catalog().getValue("chat_delete_session_confirm")
    val chat_edit_dialog_hint: String get() = catalog().getValue("chat_edit_dialog_hint")
    val chat_edit_dialog_title: String get() = catalog().getValue("chat_edit_dialog_title")
    val chat_error_title: String get() = catalog().getValue("chat_error_title")
    val chat_export_session: String get() = catalog().getValue("chat_export_session")
    val chat_export_session_done: String get() = catalog().getValue("chat_export_session_done")
    val chat_export_session_failed: String get() = catalog().getValue("chat_export_session_failed")
    val chat_fork_title: String get() = catalog().getValue("chat_fork_title")
    val chat_goal_clear: String get() = catalog().getValue("chat_goal_clear")
    val chat_goal_label: String get() = catalog().getValue("chat_goal_label")
    val chat_goal_pause: String get() = catalog().getValue("chat_goal_pause")
    val chat_goal_resume: String get() = catalog().getValue("chat_goal_resume")
    val chat_goal_status_active: String get() = catalog().getValue("chat_goal_status_active")
    val chat_goal_status_blocked: String get() = catalog().getValue("chat_goal_status_blocked")
    val chat_goal_status_completed: String get() = catalog().getValue("chat_goal_status_completed")
    val chat_goal_status_exhausted: String get() = catalog().getValue("chat_goal_status_exhausted")
    val chat_goal_status_paused: String get() = catalog().getValue("chat_goal_status_paused")
    val chat_history: String get() = catalog().getValue("chat_history")
    val chat_image_too_large: String get() = catalog().getValue("chat_image_too_large")
    val chat_input_expand: String get() = catalog().getValue("chat_input_expand")
    val chat_input_fullscreen_title: String get() = catalog().getValue("chat_input_fullscreen_title")
    val chat_input_hint: String get() = catalog().getValue("chat_input_hint")
    val chat_input_placeholder: String get() = catalog().getValue("chat_input_placeholder")
    val chat_key_switched: String get() = catalog().getValue("chat_key_switched")
    val chat_mention_empty: String get() = catalog().getValue("chat_mention_empty")
    val chat_mention_error: String get() = catalog().getValue("chat_mention_error")
    val chat_mention_loading: String get() = catalog().getValue("chat_mention_loading")
    val chat_mention_unavailable: String get() = catalog().getValue("chat_mention_unavailable")
    val chat_more_options: String get() = catalog().getValue("chat_more_options")
    val chat_new_session: String get() = catalog().getValue("chat_new_session")
    val chat_new_session_btn: String get() = catalog().getValue("chat_new_session_btn")
    val chat_no_model_selected: String get() = catalog().getValue("chat_no_model_selected")
    val chat_no_models_hint: String get() = catalog().getValue("chat_no_models_hint")
    val chat_no_remote_connection: String get() = catalog().getValue("chat_no_remote_connection")
    val chat_no_sessions_hint: String get() = catalog().getValue("chat_no_sessions_hint")
    val chat_open_apk_permission: String get() = catalog().getValue("chat_open_apk_permission")
    val chat_open_browser: String get() = catalog().getValue("chat_open_browser")
    val chat_open_file_missing: String get() = catalog().getValue("chat_open_file_missing")
    val chat_open_file_no_app: String get() = catalog().getValue("chat_open_file_no_app")
    val chat_open_file_unshareable: String get() = catalog().getValue("chat_open_file_unshareable")
    val chat_open_git: String get() = catalog().getValue("chat_open_git")
    val chat_open_sidebar: String get() = catalog().getValue("chat_open_sidebar")
    val chat_open_terminal: String get() = catalog().getValue("chat_open_terminal")
    val chat_perm_always_allow: String get() = catalog().getValue("chat_perm_always_allow")
    val chat_perm_always_prefix: String get() = catalog().getValue("chat_perm_always_prefix")
    val chat_perm_always_tool_desc: String get() = catalog().getValue("chat_perm_always_tool_desc")
    val chat_perm_deny: String get() = catalog().getValue("chat_perm_deny")
    val chat_perm_session_label: String get() = catalog().getValue("chat_perm_session_label")
    val chat_perm_single_use_desc: String get() = catalog().getValue("chat_perm_single_use_desc")
    val chat_pin_session: String get() = catalog().getValue("chat_pin_session")
    val chat_placeholder: String get() = catalog().getValue("chat_placeholder")
    val chat_plan_completed: String get() = catalog().getValue("chat_plan_completed")
    val chat_queue_drag: String get() = catalog().getValue("chat_queue_drag")
    val chat_queue_edit: String get() = catalog().getValue("chat_queue_edit")
    val chat_queue_hint: String get() = catalog().getValue("chat_queue_hint")
    val chat_queue_interject: String get() = catalog().getValue("chat_queue_interject")
    val chat_queue_remove: String get() = catalog().getValue("chat_queue_remove")
    val chat_queue_title: String get() = catalog().getValue("chat_queue_title")
    val chat_read_file_failed: String get() = catalog().getValue("chat_read_file_failed")
    val chat_reasoning_cannot_disable: String get() = catalog().getValue("chat_reasoning_cannot_disable")
    val chat_reasoning_default_tag: String get() = catalog().getValue("chat_reasoning_default_tag")
    val chat_reasoning_effective: String get() = catalog().getValue("chat_reasoning_effective")
    val chat_reasoning_effective_tag: String get() = catalog().getValue("chat_reasoning_effective_tag")
    val chat_reasoning_effort: String get() = catalog().getValue("chat_reasoning_effort")
    val chat_reasoning_effort_high: String get() = catalog().getValue("chat_reasoning_effort_high")
    val chat_reasoning_effort_low: String get() = catalog().getValue("chat_reasoning_effort_low")
    val chat_reasoning_effort_max: String get() = catalog().getValue("chat_reasoning_effort_max")
    val chat_reasoning_effort_medium: String get() = catalog().getValue("chat_reasoning_effort_medium")
    val chat_reasoning_effort_minimal: String get() = catalog().getValue("chat_reasoning_effort_minimal")
    val chat_reasoning_effort_none: String get() = catalog().getValue("chat_reasoning_effort_none")
    val chat_reasoning_effort_xhigh: String get() = catalog().getValue("chat_reasoning_effort_xhigh")
    val chat_reasoning_endpoint_weak: String get() = catalog().getValue("chat_reasoning_endpoint_weak")
    val chat_reasoning_off_short: String get() = catalog().getValue("chat_reasoning_off_short")
    val chat_reasoning_toggle: String get() = catalog().getValue("chat_reasoning_toggle")
    val chat_reasoning_unknown_hint: String get() = catalog().getValue("chat_reasoning_unknown_hint")
    val chat_remote_connect_failed: String get() = catalog().getValue("chat_remote_connect_failed")
    val chat_remove_attachment: String get() = catalog().getValue("chat_remove_attachment")
    val chat_rename_session: String get() = catalog().getValue("chat_rename_session")
    val chat_retrying: String get() = catalog().getValue("chat_retrying")
    val chat_search_clear: String get() = catalog().getValue("chat_search_clear")
    val chat_search_hint: String get() = catalog().getValue("chat_search_hint")
    val chat_search_no_result: String get() = catalog().getValue("chat_search_no_result")
    val chat_select_workspace_first: String get() = catalog().getValue("chat_select_workspace_first")
    val chat_send: String get() = catalog().getValue("chat_send")
    val chat_session_name: String get() = catalog().getValue("chat_session_name")
    val chat_sessions_selected_count: String get() = catalog().getValue("chat_sessions_selected_count")
    val chat_settings: String get() = catalog().getValue("chat_settings")
    val chat_ssh_connected: String get() = catalog().getValue("chat_ssh_connected")
    val chat_ssh_connecting: String get() = catalog().getValue("chat_ssh_connecting")
    val chat_ssh_disconnected: String get() = catalog().getValue("chat_ssh_disconnected")
    val chat_ssh_failed: String get() = catalog().getValue("chat_ssh_failed")
    val chat_status_asking_user: String get() = catalog().getValue("chat_status_asking_user")
    val chat_status_browsing_web: String get() = catalog().getValue("chat_status_browsing_web")
    val chat_status_calling_tool: String get() = catalog().getValue("chat_status_calling_tool")
    val chat_status_editing_file: String get() = catalog().getValue("chat_status_editing_file")
    val chat_status_generating: String get() = catalog().getValue("chat_status_generating")
    val chat_status_generating_image: String get() = catalog().getValue("chat_status_generating_image")
    val chat_status_reading_file: String get() = catalog().getValue("chat_status_reading_file")
    val chat_status_reading_memory: String get() = catalog().getValue("chat_status_reading_memory")
    val chat_status_running_command: String get() = catalog().getValue("chat_status_running_command")
    val chat_status_searching_web: String get() = catalog().getValue("chat_status_searching_web")
    val chat_status_starting_subagent: String get() = catalog().getValue("chat_status_starting_subagent")
    val chat_status_thinking: String get() = catalog().getValue("chat_status_thinking")
    val chat_status_updating_todo: String get() = catalog().getValue("chat_status_updating_todo")
    val chat_status_viewing_image: String get() = catalog().getValue("chat_status_viewing_image")
    val chat_stop: String get() = catalog().getValue("chat_stop")
    val chat_take_photo: String get() = catalog().getValue("chat_take_photo")
    val chat_task_duration: String get() = catalog().getValue("chat_task_duration")
    val chat_thinking_done: String get() = catalog().getValue("chat_thinking_done")
    val chat_thinking_done_time: String get() = catalog().getValue("chat_thinking_done_time")
    val chat_thinking_process: String get() = catalog().getValue("chat_thinking_process")
    val chat_thinking_running_time: String get() = catalog().getValue("chat_thinking_running_time")
    val chat_tool_calls_count: String get() = catalog().getValue("chat_tool_calls_count")
    val chat_tool_failed: String get() = catalog().getValue("chat_tool_failed")
    val chat_tool_running: String get() = catalog().getValue("chat_tool_running")
    val chat_tool_succeeded: String get() = catalog().getValue("chat_tool_succeeded")
    val chat_turn_completed: String get() = catalog().getValue("chat_turn_completed")
    val chat_turn_completed_plain: String get() = catalog().getValue("chat_turn_completed_plain")
    val chat_turn_running: String get() = catalog().getValue("chat_turn_running")
    val chat_unpin_session: String get() = catalog().getValue("chat_unpin_session")
    val chat_unsupported_image_type: String get() = catalog().getValue("chat_unsupported_image_type")
    val chat_upload_failed: String get() = catalog().getValue("chat_upload_failed")
    val chat_upload_file: String get() = catalog().getValue("chat_upload_file")
    val chat_upload_image: String get() = catalog().getValue("chat_upload_image")
    val chat_uploaded_to: String get() = catalog().getValue("chat_uploaded_to")
    val chat_workspace_unavailable: String get() = catalog().getValue("chat_workspace_unavailable")
    val checkpoint_restore_code: String get() = catalog().getValue("checkpoint_restore_code")
    val checkpoint_restore_code_and_conversation: String get() = catalog().getValue("checkpoint_restore_code_and_conversation")
    val checkpoint_restore_code_and_conversation_desc: String get() = catalog().getValue("checkpoint_restore_code_and_conversation_desc")
    val checkpoint_restore_code_desc: String get() = catalog().getValue("checkpoint_restore_code_desc")
    val checkpoint_restore_conversation: String get() = catalog().getValue("checkpoint_restore_conversation")
    val checkpoint_restore_conversation_desc: String get() = catalog().getValue("checkpoint_restore_conversation_desc")
    val checkpoint_rewind_title: String get() = catalog().getValue("checkpoint_rewind_title")
    val checkpoint_skipped_multiple: String get() = catalog().getValue("checkpoint_skipped_multiple")
    val checkpoint_skipped_single: String get() = catalog().getValue("checkpoint_skipped_single")
    val checkpoint_target_prompt: String get() = catalog().getValue("checkpoint_target_prompt")
    val common_add: String get() = catalog().getValue("common_add")
    val common_ai_providers: String get() = catalog().getValue("common_ai_providers")
    val common_allow: String get() = catalog().getValue("common_allow")
    val common_back: String get() = catalog().getValue("common_back")
    val common_batch_delete: String get() = catalog().getValue("common_batch_delete")
    val common_batch_deleting: String get() = catalog().getValue("common_batch_deleting")
    val common_batch_disable: String get() = catalog().getValue("common_batch_disable")
    val common_batch_enable: String get() = catalog().getValue("common_batch_enable")
    val common_batch_reconnect: String get() = catalog().getValue("common_batch_reconnect")
    val common_cancel: String get() = catalog().getValue("common_cancel")
    val common_clear: String get() = catalog().getValue("common_clear")
    val common_close: String get() = catalog().getValue("common_close")
    val common_collapse: String get() = catalog().getValue("common_collapse")
    val common_collapse_action: String get() = catalog().getValue("common_collapse_action")
    val common_confirm: String get() = catalog().getValue("common_confirm")
    val common_copied_with_title: String get() = catalog().getValue("common_copied_with_title")
    val common_copy: String get() = catalog().getValue("common_copy")
    val common_copy_success: String get() = catalog().getValue("common_copy_success")
    val common_create: String get() = catalog().getValue("common_create")
    val common_cut: String get() = catalog().getValue("common_cut")
    val common_delete: String get() = catalog().getValue("common_delete")
    val common_deselect_all: String get() = catalog().getValue("common_deselect_all")
    val common_disabled: String get() = catalog().getValue("common_disabled")
    val common_done: String get() = catalog().getValue("common_done")
    val common_edit: String get() = catalog().getValue("common_edit")
    val common_enabled: String get() = catalog().getValue("common_enabled")
    val common_error: String get() = catalog().getValue("common_error")
    val common_error_unknown: String get() = catalog().getValue("common_error_unknown")
    val common_expand: String get() = catalog().getValue("common_expand")
    val common_file: String get() = catalog().getValue("common_file")
    val common_go_to_settings: String get() = catalog().getValue("common_go_to_settings")
    val common_got_it: String get() = catalog().getValue("common_got_it")
    val common_hide: String get() = catalog().getValue("common_hide")
    val common_image_load_failed: String get() = catalog().getValue("common_image_load_failed")
    val common_image_preview: String get() = catalog().getValue("common_image_preview")
    val common_label_with_api: String get() = catalog().getValue("common_label_with_api")
    val common_local: String get() = catalog().getValue("common_local")
    val common_model: String get() = catalog().getValue("common_model")
    val common_more: String get() = catalog().getValue("common_more")
    val common_name: String get() = catalog().getValue("common_name")
    val common_new_tab: String get() = catalog().getValue("common_new_tab")
    val common_next: String get() = catalog().getValue("common_next")
    val common_open_link_failed: String get() = catalog().getValue("common_open_link_failed")
    val common_other: String get() = catalog().getValue("common_other")
    val common_overwrite: String get() = catalog().getValue("common_overwrite")
    val common_paste: String get() = catalog().getValue("common_paste")
    val common_prev: String get() = catalog().getValue("common_prev")
    val common_rename: String get() = catalog().getValue("common_rename")
    val common_reset: String get() = catalog().getValue("common_reset")
    val common_retry: String get() = catalog().getValue("common_retry")
    val common_save: String get() = catalog().getValue("common_save")
    val common_scroll_to_bottom: String get() = catalog().getValue("common_scroll_to_bottom")
    val common_select: String get() = catalog().getValue("common_select")
    val common_select_all: String get() = catalog().getValue("common_select_all")
    val common_select_mode: String get() = catalog().getValue("common_select_mode")
    val common_selected_count: String get() = catalog().getValue("common_selected_count")
    val common_show: String get() = catalog().getValue("common_show")
    val common_switch: String get() = catalog().getValue("common_switch")
    val common_tool: String get() = catalog().getValue("common_tool")
    val common_tool_missing_argument: String get() = catalog().getValue("common_tool_missing_argument")
    val common_username: String get() = catalog().getValue("common_username")
    val common_workspace: String get() = catalog().getValue("common_workspace")
    val compaction_no_models: String get() = catalog().getValue("compaction_no_models")
    val container_add_arg: String get() = catalog().getValue("container_add_arg")
    val container_add_binding: String get() = catalog().getValue("container_add_binding")
    val container_add_env_var: String get() = catalog().getValue("container_add_env_var")
    val container_add_image: String get() = catalog().getValue("container_add_image")
    val container_announcement_got_it: String get() = catalog().getValue("container_announcement_got_it")
    val container_announcement_title: String get() = catalog().getValue("container_announcement_title")
    val container_arg_value: String get() = catalog().getValue("container_arg_value")
    val container_builtin_badge: String get() = catalog().getValue("container_builtin_badge")
    val container_builtin_restorable: String get() = catalog().getValue("container_builtin_restorable")
    val container_channel_deleted: String get() = catalog().getValue("container_channel_deleted")
    val container_custom_badge: String get() = catalog().getValue("container_custom_badge")
    val container_custom_image: String get() = catalog().getValue("container_custom_image")
    val container_default_badge: String get() = catalog().getValue("container_default_badge")
    val container_default_desc: String get() = catalog().getValue("container_default_desc")
    val container_delete_config: String get() = catalog().getValue("container_delete_config")
    val container_delete_confirm: String get() = catalog().getValue("container_delete_confirm")
    val container_download_architecture: String get() = catalog().getValue("container_download_architecture")
    val container_download_batch_delete_confirm: String get() = catalog().getValue("container_download_batch_delete_confirm")
    val container_download_batch_delete_title: String get() = catalog().getValue("container_download_batch_delete_title")
    val container_download_cancel: String get() = catalog().getValue("container_download_cancel")
    val container_download_checksum_failed: String get() = catalog().getValue("container_download_checksum_failed")
    val container_download_current_source: String get() = catalog().getValue("container_download_current_source")
    val container_download_current_source_title: String get() = catalog().getValue("container_download_current_source_title")
    val container_download_delete_confirm: String get() = catalog().getValue("container_download_delete_confirm")
    val container_download_delete_title: String get() = catalog().getValue("container_download_delete_title")
    val container_download_done: String get() = catalog().getValue("container_download_done")
    val container_download_eol_ended: String get() = catalog().getValue("container_download_eol_ended")
    val container_download_eol_extended: String get() = catalog().getValue("container_download_eol_extended")
    val container_download_eol_extended_generic: String get() = catalog().getValue("container_download_eol_extended_generic")
    val container_download_eol_fully_ends: String get() = catalog().getValue("container_download_eol_fully_ends")
    val container_download_eol_soon: String get() = catalog().getValue("container_download_eol_soon")
    val container_download_eol_support_ends: String get() = catalog().getValue("container_download_eol_support_ends")
    val container_download_failed: String get() = catalog().getValue("container_download_failed")
    val container_download_image: String get() = catalog().getValue("container_download_image")
    val container_download_import: String get() = catalog().getValue("container_download_import")
    val container_download_imported_toast: String get() = catalog().getValue("container_download_imported_toast")
    val container_download_list_empty: String get() = catalog().getValue("container_download_list_empty")
    val container_download_newer_available: String get() = catalog().getValue("container_download_newer_available")
    val container_download_redownload: String get() = catalog().getValue("container_download_redownload")
    val container_download_size: String get() = catalog().getValue("container_download_size")
    val container_download_source_unavailable: String get() = catalog().getValue("container_download_source_unavailable")
    val container_download_start: String get() = catalog().getValue("container_download_start")
    val container_edit_image: String get() = catalog().getValue("container_edit_image")
    val container_empty: String get() = catalog().getValue("container_empty")
    val container_empty_desc: String get() = catalog().getValue("container_empty_desc")
    val container_env_name: String get() = catalog().getValue("container_env_name")
    val container_env_value: String get() = catalog().getValue("container_env_value")
    val container_env_vars: String get() = catalog().getValue("container_env_vars")
    val container_extra_bindings: String get() = catalog().getValue("container_extra_bindings")
    val container_extra_proot_args: String get() = catalog().getValue("container_extra_proot_args")
    val container_file_selected: String get() = catalog().getValue("container_file_selected")
    val container_importing: String get() = catalog().getValue("container_importing")
    val container_local_image: String get() = catalog().getValue("container_local_image")
    val container_mount_container: String get() = catalog().getValue("container_mount_container")
    val container_mount_local: String get() = catalog().getValue("container_mount_local")
    val container_no_args: String get() = catalog().getValue("container_no_args")
    val container_no_bindings: String get() = catalog().getValue("container_no_bindings")
    val container_no_env_vars: String get() = catalog().getValue("container_no_env_vars")
    val container_no_sftp_channel: String get() = catalog().getValue("container_no_sftp_channel")
    val container_not_ready_hint: String get() = catalog().getValue("container_not_ready_hint")
    val container_remote_ssh: String get() = catalog().getValue("container_remote_ssh")
    val container_remote_workspace_path: String get() = catalog().getValue("container_remote_workspace_path")
    val container_reset: String get() = catalog().getValue("container_reset")
    val container_reset_confirm: String get() = catalog().getValue("container_reset_confirm")
    val container_reset_title: String get() = catalog().getValue("container_reset_title")
    val container_resetting_message: String get() = catalog().getValue("container_resetting_message")
    val container_resetting_title: String get() = catalog().getValue("container_resetting_title")
    val container_restore_builtin: String get() = catalog().getValue("container_restore_builtin")
    val container_rootfs_will_be_cleared: String get() = catalog().getValue("container_rootfs_will_be_cleared")
    val container_select_image_file: String get() = catalog().getValue("container_select_image_file")
    val container_select_ssh_channel: String get() = catalog().getValue("container_select_ssh_channel")
    val container_set_as_default: String get() = catalog().getValue("container_set_as_default")
    val container_shell_desc: String get() = catalog().getValue("container_shell_desc")
    val container_shell_path: String get() = catalog().getValue("container_shell_path")
    val container_sort_long_press: String get() = catalog().getValue("container_sort_long_press")
    val container_ssh_badge: String get() = catalog().getValue("container_ssh_badge")
    val container_ssh_channel: String get() = catalog().getValue("container_ssh_channel")
    val container_switch_confirm: String get() = catalog().getValue("container_switch_confirm")
    val container_switch_image: String get() = catalog().getValue("container_switch_image")
    val crash_banner_title: String get() = catalog().getValue("crash_banner_title")
    val crash_copied: String get() = catalog().getValue("crash_copied")
    val crash_copy: String get() = catalog().getValue("crash_copy")
    val crash_exit: String get() = catalog().getValue("crash_exit")
    val crash_field_device: String get() = catalog().getValue("crash_field_device")
    val crash_field_memory: String get() = catalog().getValue("crash_field_memory")
    val crash_field_screen: String get() = catalog().getValue("crash_field_screen")
    val crash_field_thread: String get() = catalog().getValue("crash_field_thread")
    val crash_field_time: String get() = catalog().getValue("crash_field_time")
    val crash_field_version: String get() = catalog().getValue("crash_field_version")
    val crash_field_workspace_mode: String get() = catalog().getValue("crash_field_workspace_mode")
    val crash_log_hint: String get() = catalog().getValue("crash_log_hint")
    val crash_message: String get() = catalog().getValue("crash_message")
    val crash_mode_local: String get() = catalog().getValue("crash_mode_local")
    val crash_mode_remote: String get() = catalog().getValue("crash_mode_remote")
    val crash_mode_unknown: String get() = catalog().getValue("crash_mode_unknown")
    val crash_report_device: String get() = catalog().getValue("crash_report_device")
    val crash_report_log: String get() = catalog().getValue("crash_report_log")
    val crash_report_memory: String get() = catalog().getValue("crash_report_memory")
    val crash_report_mode: String get() = catalog().getValue("crash_report_mode")
    val crash_report_screen: String get() = catalog().getValue("crash_report_screen")
    val crash_report_thread: String get() = catalog().getValue("crash_report_thread")
    val crash_report_time: String get() = catalog().getValue("crash_report_time")
    val crash_report_title: String get() = catalog().getValue("crash_report_title")
    val crash_report_version: String get() = catalog().getValue("crash_report_version")
    val crash_restart: String get() = catalog().getValue("crash_restart")
    val crash_screen_chat: String get() = catalog().getValue("crash_screen_chat")
    val crash_screen_git: String get() = catalog().getValue("crash_screen_git")
    val crash_screen_settings: String get() = catalog().getValue("crash_screen_settings")
    val crash_screen_terminal: String get() = catalog().getValue("crash_screen_terminal")
    val crash_screen_unknown: String get() = catalog().getValue("crash_screen_unknown")
    val crash_section_environment: String get() = catalog().getValue("crash_section_environment")
    val crash_section_stack: String get() = catalog().getValue("crash_section_stack")
    val crash_title: String get() = catalog().getValue("crash_title")
    val credential_add: String get() = catalog().getValue("credential_add")
    val credential_add_github_desc: String get() = catalog().getValue("credential_add_github_desc")
    val credential_add_github_title: String get() = catalog().getValue("credential_add_github_title")
    val credential_add_manual: String get() = catalog().getValue("credential_add_manual")
    val credential_add_manual_desc: String get() = catalog().getValue("credential_add_manual_desc")
    val credential_add_manual_subtitle: String get() = catalog().getValue("credential_add_manual_subtitle")
    val credential_add_manual_title: String get() = catalog().getValue("credential_add_manual_title")
    val credential_add_recommend: String get() = catalog().getValue("credential_add_recommend")
    val credential_add_sheet_subtitle: String get() = catalog().getValue("credential_add_sheet_subtitle")
    val credential_add_sheet_title: String get() = catalog().getValue("credential_add_sheet_title")
    val credential_auto_inject_enable: String get() = catalog().getValue("credential_auto_inject_enable")
    val credential_auto_inject_hint: String get() = catalog().getValue("credential_auto_inject_hint")
    val credential_auto_inject_prompt_body: String get() = catalog().getValue("credential_auto_inject_prompt_body")
    val credential_auto_inject_prompt_title: String get() = catalog().getValue("credential_auto_inject_prompt_title")
    val credential_auto_inject_title: String get() = catalog().getValue("credential_auto_inject_title")
    val credential_device_authorize_and_open: String get() = catalog().getValue("credential_device_authorize_and_open")
    val credential_device_bad_client: String get() = catalog().getValue("credential_device_bad_client")
    val credential_device_card_label: String get() = catalog().getValue("credential_device_card_label")
    val credential_device_code_copied: String get() = catalog().getValue("credential_device_code_copied")
    val credential_device_code_copied_opening: String get() = catalog().getValue("credential_device_code_copied_opening")
    val credential_device_copy_code: String get() = catalog().getValue("credential_device_copy_code")
    val credential_device_denied: String get() = catalog().getValue("credential_device_denied")
    val credential_device_expired: String get() = catalog().getValue("credential_device_expired")
    val credential_device_failed: String get() = catalog().getValue("credential_device_failed")
    val credential_device_failed_title: String get() = catalog().getValue("credential_device_failed_title")
    val credential_device_flow_disabled: String get() = catalog().getValue("credential_device_flow_disabled")
    val credential_device_install_later: String get() = catalog().getValue("credential_device_install_later")
    val credential_device_install_now: String get() = catalog().getValue("credential_device_install_now")
    val credential_device_install_required: String get() = catalog().getValue("credential_device_install_required")
    val credential_device_instruction: String get() = catalog().getValue("credential_device_instruction")
    val credential_device_open_browser: String get() = catalog().getValue("credential_device_open_browser")
    val credential_device_requesting: String get() = catalog().getValue("credential_device_requesting")
    val credential_device_step_1: String get() = catalog().getValue("credential_device_step_1")
    val credential_device_step_2: String get() = catalog().getValue("credential_device_step_2")
    val credential_device_subtitle: String get() = catalog().getValue("credential_device_subtitle")
    val credential_device_success: String get() = catalog().getValue("credential_device_success")
    val credential_device_success_title: String get() = catalog().getValue("credential_device_success_title")
    val credential_device_tap_to_copy: String get() = catalog().getValue("credential_device_tap_to_copy")
    val credential_device_title: String get() = catalog().getValue("credential_device_title")
    val credential_device_unknown_error: String get() = catalog().getValue("credential_device_unknown_error")
    val credential_device_waiting: String get() = catalog().getValue("credential_device_waiting")
    val credential_device_waiting_desc: String get() = catalog().getValue("credential_device_waiting_desc")
    val credential_edit: String get() = catalog().getValue("credential_edit")
    val credential_empty_desc: String get() = catalog().getValue("credential_empty_desc")
    val credential_empty_hint: String get() = catalog().getValue("credential_empty_hint")
    val credential_empty_title: String get() = catalog().getValue("credential_empty_title")
    val credential_host: String get() = catalog().getValue("credential_host")
    val credential_prompt_desc: String get() = catalog().getValue("credential_prompt_desc")
    val credential_prompt_title: String get() = catalog().getValue("credential_prompt_title")
    val credential_quick_login: String get() = catalog().getValue("credential_quick_login")
    val credential_quick_login_desc: String get() = catalog().getValue("credential_quick_login_desc")
    val credential_quick_login_github_desc: String get() = catalog().getValue("credential_quick_login_github_desc")
    val credential_quick_login_title: String get() = catalog().getValue("credential_quick_login_title")
    val credential_remote_host: String get() = catalog().getValue("credential_remote_host")
    val credential_save_and_retry: String get() = catalog().getValue("credential_save_and_retry")
    val credential_toast_deleted: String get() = catalog().getValue("credential_toast_deleted")
    val credential_toast_identity_saved: String get() = catalog().getValue("credential_toast_identity_saved")
    val credential_toast_save_failed: String get() = catalog().getValue("credential_toast_save_failed")
    val credential_toast_saved: String get() = catalog().getValue("credential_toast_saved")
    val credential_token: String get() = catalog().getValue("credential_token")
    val dashboard_exec_failed: String get() = catalog().getValue("dashboard_exec_failed")
    val dashboard_fetch_failed_retry: String get() = catalog().getValue("dashboard_fetch_failed_retry")
    val dashboard_fetching: String get() = catalog().getValue("dashboard_fetching")
    val dashboard_query_failed: String get() = catalog().getValue("dashboard_query_failed")
    val dashboard_tap_to_query: String get() = catalog().getValue("dashboard_tap_to_query")
    val diff_binary_file: String get() = catalog().getValue("diff_binary_file")
    val diff_file_too_large: String get() = catalog().getValue("diff_file_too_large")
    val diff_file_too_large_desc: String get() = catalog().getValue("diff_file_too_large_desc")
    val diff_no_changes: String get() = catalog().getValue("diff_no_changes")
    val drawer_collapse_subagents: String get() = catalog().getValue("drawer_collapse_subagents")
    val drawer_expand_subagents: String get() = catalog().getValue("drawer_expand_subagents")
    val drawer_tab_files: String get() = catalog().getValue("drawer_tab_files")
    val editor_binary_unsupported: String get() = catalog().getValue("editor_binary_unsupported")
    val editor_cursor_left: String get() = catalog().getValue("editor_cursor_left")
    val editor_cursor_right: String get() = catalog().getValue("editor_cursor_right")
    val editor_discard: String get() = catalog().getValue("editor_discard")
    val editor_dont_save: String get() = catalog().getValue("editor_dont_save")
    val editor_file_too_large: String get() = catalog().getValue("editor_file_too_large")
    val editor_font_size: String get() = catalog().getValue("editor_font_size")
    val editor_font_size_value: String get() = catalog().getValue("editor_font_size_value")
    val editor_indent: String get() = catalog().getValue("editor_indent")
    val editor_load_failed: String get() = catalog().getValue("editor_load_failed")
    val editor_md_preview: String get() = catalog().getValue("editor_md_preview")
    val editor_md_show_source: String get() = catalog().getValue("editor_md_show_source")
    val editor_redo: String get() = catalog().getValue("editor_redo")
    val editor_save_and_exit: String get() = catalog().getValue("editor_save_and_exit")
    val editor_save_and_switch: String get() = catalog().getValue("editor_save_and_switch")
    val editor_save_conflict_cancel: String get() = catalog().getValue("editor_save_conflict_cancel")
    val editor_save_conflict_diff: String get() = catalog().getValue("editor_save_conflict_diff")
    val editor_save_conflict_discard: String get() = catalog().getValue("editor_save_conflict_discard")
    val editor_save_conflict_discarded: String get() = catalog().getValue("editor_save_conflict_discarded")
    val editor_save_conflict_message: String get() = catalog().getValue("editor_save_conflict_message")
    val editor_save_conflict_overwrite: String get() = catalog().getValue("editor_save_conflict_overwrite")
    val editor_save_conflict_title: String get() = catalog().getValue("editor_save_conflict_title")
    val editor_save_failed: String get() = catalog().getValue("editor_save_failed")
    val editor_save_success: String get() = catalog().getValue("editor_save_success")
    val editor_settings: String get() = catalog().getValue("editor_settings")
    val editor_show_indent_guide: String get() = catalog().getValue("editor_show_indent_guide")
    val editor_show_whitespace: String get() = catalog().getValue("editor_show_whitespace")
    val editor_show_wrap_arrow: String get() = catalog().getValue("editor_show_wrap_arrow")
    val editor_undo: String get() = catalog().getValue("editor_undo")
    val editor_unsaved_message: String get() = catalog().getValue("editor_unsaved_message")
    val editor_unsaved_title: String get() = catalog().getValue("editor_unsaved_title")
    val editor_word_wrap: String get() = catalog().getValue("editor_word_wrap")
    val file_browser_clipboard_copy: String get() = catalog().getValue("file_browser_clipboard_copy")
    val file_browser_clipboard_cut: String get() = catalog().getValue("file_browser_clipboard_cut")
    val file_browser_collapse: String get() = catalog().getValue("file_browser_collapse")
    val file_browser_create_failed: String get() = catalog().getValue("file_browser_create_failed")
    val file_browser_delete_failed: String get() = catalog().getValue("file_browser_delete_failed")
    val file_browser_delete_file_confirm: String get() = catalog().getValue("file_browser_delete_file_confirm")
    val file_browser_delete_folder_confirm: String get() = catalog().getValue("file_browser_delete_folder_confirm")
    val file_browser_empty: String get() = catalog().getValue("file_browser_empty")
    val file_browser_error: String get() = catalog().getValue("file_browser_error")
    val file_browser_expand: String get() = catalog().getValue("file_browser_expand")
    val file_browser_name_label: String get() = catalog().getValue("file_browser_name_label")
    val file_browser_new_file: String get() = catalog().getValue("file_browser_new_file")
    val file_browser_new_folder: String get() = catalog().getValue("file_browser_new_folder")
    val file_browser_paste_conflict_message: String get() = catalog().getValue("file_browser_paste_conflict_message")
    val file_browser_paste_conflict_title: String get() = catalog().getValue("file_browser_paste_conflict_title")
    val file_browser_paste_here: String get() = catalog().getValue("file_browser_paste_here")
    val file_browser_refresh: String get() = catalog().getValue("file_browser_refresh")
    val file_browser_rename_failed: String get() = catalog().getValue("file_browser_rename_failed")
    val file_browser_up: String get() = catalog().getValue("file_browser_up")
    val ftp_address_copied: String get() = catalog().getValue("ftp_address_copied")
    val ftp_allow_anonymous: String get() = catalog().getValue("ftp_allow_anonymous")
    val ftp_anonymous_confirm_message: String get() = catalog().getValue("ftp_anonymous_confirm_message")
    val ftp_anonymous_confirm_title: String get() = catalog().getValue("ftp_anonymous_confirm_title")
    val ftp_anonymous_desc: String get() = catalog().getValue("ftp_anonymous_desc")
    val ftp_auto_start: String get() = catalog().getValue("ftp_auto_start")
    val ftp_auto_start_desc: String get() = catalog().getValue("ftp_auto_start_desc")
    val ftp_config_saved: String get() = catalog().getValue("ftp_config_saved")
    val ftp_config_title: String get() = catalog().getValue("ftp_config_title")
    val ftp_copy_address: String get() = catalog().getValue("ftp_copy_address")
    val ftp_error: String get() = catalog().getValue("ftp_error")
    val ftp_listen_port: String get() = catalog().getValue("ftp_listen_port")
    val ftp_login_password: String get() = catalog().getValue("ftp_login_password")
    val ftp_login_username: String get() = catalog().getValue("ftp_login_username")
    val ftp_not_running: String get() = catalog().getValue("ftp_not_running")
    val ftp_password_required: String get() = catalog().getValue("ftp_password_required")
    val ftp_reserved_username: String get() = catalog().getValue("ftp_reserved_username")
    val ftp_running: String get() = catalog().getValue("ftp_running")
    val ftp_save_config: String get() = catalog().getValue("ftp_save_config")
    val ftp_usage_item_1: String get() = catalog().getValue("ftp_usage_item_1")
    val ftp_usage_item_2: String get() = catalog().getValue("ftp_usage_item_2")
    val ftp_usage_item_3: String get() = catalog().getValue("ftp_usage_item_3")
    val ftp_usage_title: String get() = catalog().getValue("ftp_usage_title")
    val ftp_user_cache_cleanup_failed: String get() = catalog().getValue("ftp_user_cache_cleanup_failed")
    val git_abort_merge_confirm: String get() = catalog().getValue("git_abort_merge_confirm")
    val git_action_abort_merge: String get() = catalog().getValue("git_action_abort_merge")
    val git_action_clone: String get() = catalog().getValue("git_action_clone")
    val git_action_commit: String get() = catalog().getValue("git_action_commit")
    val git_action_copy_path: String get() = catalog().getValue("git_action_copy_path")
    val git_action_create_branch: String get() = catalog().getValue("git_action_create_branch")
    val git_action_create_tag: String get() = catalog().getValue("git_action_create_tag")
    val git_action_delete_branch: String get() = catalog().getValue("git_action_delete_branch")
    val git_action_delete_dir: String get() = catalog().getValue("git_action_delete_dir")
    val git_action_delete_file: String get() = catalog().getValue("git_action_delete_file")
    val git_action_delete_remote_branch: String get() = catalog().getValue("git_action_delete_remote_branch")
    val git_action_delete_tag: String get() = catalog().getValue("git_action_delete_tag")
    val git_action_init: String get() = catalog().getValue("git_action_init")
    val git_action_merge: String get() = catalog().getValue("git_action_merge")
    val git_action_rename_branch: String get() = catalog().getValue("git_action_rename_branch")
    val git_action_restore_file: String get() = catalog().getValue("git_action_restore_file")
    val git_action_revert: String get() = catalog().getValue("git_action_revert")
    val git_action_revert_all: String get() = catalog().getValue("git_action_revert_all")
    val git_action_stage_all: String get() = catalog().getValue("git_action_stage_all")
    val git_action_stash: String get() = catalog().getValue("git_action_stash")
    val git_action_unstage_all: String get() = catalog().getValue("git_action_unstage_all")
    val git_action_view_diff: String get() = catalog().getValue("git_action_view_diff")
    val git_add_to_gitignore: String get() = catalog().getValue("git_add_to_gitignore")
    val git_ahead_count: String get() = catalog().getValue("git_ahead_count")
    val git_base_branch: String get() = catalog().getValue("git_base_branch")
    val git_behind_count: String get() = catalog().getValue("git_behind_count")
    val git_branch_name: String get() = catalog().getValue("git_branch_name")
    val git_branch_synced: String get() = catalog().getValue("git_branch_synced")
    val git_checked_out: String get() = catalog().getValue("git_checked_out")
    val git_clean: String get() = catalog().getValue("git_clean")
    val git_clone_desc: String get() = catalog().getValue("git_clone_desc")
    val git_clone_dialog_title: String get() = catalog().getValue("git_clone_dialog_title")
    val git_clone_repo: String get() = catalog().getValue("git_clone_repo")
    val git_clone_url_hint: String get() = catalog().getValue("git_clone_url_hint")
    val git_clone_url_label: String get() = catalog().getValue("git_clone_url_label")
    val git_cloning_hint: String get() = catalog().getValue("git_cloning_hint")
    val git_commit_ai_generate: String get() = catalog().getValue("git_commit_ai_generate")
    val git_commit_amend: String get() = catalog().getValue("git_commit_amend")
    val git_commit_changes: String get() = catalog().getValue("git_commit_changes")
    val git_commit_count: String get() = catalog().getValue("git_commit_count")
    val git_commit_dialog_title: String get() = catalog().getValue("git_commit_dialog_title")
    val git_commit_generating: String get() = catalog().getValue("git_commit_generating")
    val git_commit_message: String get() = catalog().getValue("git_commit_message")
    val git_commit_needs_identity: String get() = catalog().getValue("git_commit_needs_identity")
    val git_computing_diff: String get() = catalog().getValue("git_computing_diff")
    val git_conflicted_count: String get() = catalog().getValue("git_conflicted_count")
    val git_copy_full_hash: String get() = catalog().getValue("git_copy_full_hash")
    val git_copy_short_hash: String get() = catalog().getValue("git_copy_short_hash")
    val git_create_and_switch: String get() = catalog().getValue("git_create_and_switch")
    val git_create_branch_from_commit: String get() = catalog().getValue("git_create_branch_from_commit")
    val git_create_tag_at_commit: String get() = catalog().getValue("git_create_tag_at_commit")
    val git_create_tag_desc: String get() = catalog().getValue("git_create_tag_desc")
    val git_create_tag_title: String get() = catalog().getValue("git_create_tag_title")
    val git_create_tracking_branch: String get() = catalog().getValue("git_create_tracking_branch")
    val git_credentials_and_identity: String get() = catalog().getValue("git_credentials_and_identity")
    val git_credentials_count: String get() = catalog().getValue("git_credentials_count")
    val git_current_branch: String get() = catalog().getValue("git_current_branch")
    val git_delete_branch: String get() = catalog().getValue("git_delete_branch")
    val git_delete_local_branch_confirm: String get() = catalog().getValue("git_delete_local_branch_confirm")
    val git_delete_remote_branch: String get() = catalog().getValue("git_delete_remote_branch")
    val git_delete_remote_branch_confirm: String get() = catalog().getValue("git_delete_remote_branch_confirm")
    val git_delete_tag: String get() = catalog().getValue("git_delete_tag")
    val git_delete_tag_confirm: String get() = catalog().getValue("git_delete_tag_confirm")
    val git_delete_untracked_confirm: String get() = catalog().getValue("git_delete_untracked_confirm")
    val git_detached_head_label: String get() = catalog().getValue("git_detached_head_label")
    val git_detached_head_warning: String get() = catalog().getValue("git_detached_head_warning")
    val git_expanded: String get() = catalog().getValue("git_expanded")
    val git_files_changed: String get() = catalog().getValue("git_files_changed")
    val git_has_changes: String get() = catalog().getValue("git_has_changes")
    val git_identity_desc: String get() = catalog().getValue("git_identity_desc")
    val git_identity_title: String get() = catalog().getValue("git_identity_title")
    val git_ignore_dir: String get() = catalog().getValue("git_ignore_dir")
    val git_ignore_extension: String get() = catalog().getValue("git_ignore_extension")
    val git_ignore_file: String get() = catalog().getValue("git_ignore_file")
    val git_init_desc_primary: String get() = catalog().getValue("git_init_desc_primary")
    val git_init_desc_secondary: String get() = catalog().getValue("git_init_desc_secondary")
    val git_init_repo: String get() = catalog().getValue("git_init_repo")
    val git_latest_commit: String get() = catalog().getValue("git_latest_commit")
    val git_latest_date: String get() = catalog().getValue("git_latest_date")
    val git_load_more_commits: String get() = catalog().getValue("git_load_more_commits")
    val git_loading_branches: String get() = catalog().getValue("git_loading_branches")
    val git_loading_files: String get() = catalog().getValue("git_loading_files")
    val git_merge_commit: String get() = catalog().getValue("git_merge_commit")
    val git_merge_confirm: String get() = catalog().getValue("git_merge_confirm")
    val git_merge_into: String get() = catalog().getValue("git_merge_into")
    val git_merge_no_ff: String get() = catalog().getValue("git_merge_no_ff")
    val git_merge_pending_hint: String get() = catalog().getValue("git_merge_pending_hint")
    val git_merge_pending_title: String get() = catalog().getValue("git_merge_pending_title")
    val git_modified_count: String get() = catalog().getValue("git_modified_count")
    val git_modified_label: String get() = catalog().getValue("git_modified_label")
    val git_new: String get() = catalog().getValue("git_new")
    val git_new_branch: String get() = catalog().getValue("git_new_branch")
    val git_new_branch_name: String get() = catalog().getValue("git_new_branch_name")
    val git_no_branch: String get() = catalog().getValue("git_no_branch")
    val git_no_branches: String get() = catalog().getValue("git_no_branches")
    val git_no_checked_out_branch: String get() = catalog().getValue("git_no_checked_out_branch")
    val git_no_commits: String get() = catalog().getValue("git_no_commits")
    val git_no_files_changed: String get() = catalog().getValue("git_no_files_changed")
    val git_no_identity: String get() = catalog().getValue("git_no_identity")
    val git_no_more_commits: String get() = catalog().getValue("git_no_more_commits")
    val git_no_upstream: String get() = catalog().getValue("git_no_upstream")
    val git_not_a_repo: String get() = catalog().getValue("git_not_a_repo")
    val git_open_in_editor: String get() = catalog().getValue("git_open_in_editor")
    val git_pick_existing_repo: String get() = catalog().getValue("git_pick_existing_repo")
    val git_pick_repo_empty: String get() = catalog().getValue("git_pick_repo_empty")
    val git_pull: String get() = catalog().getValue("git_pull")
    val git_pull_continue: String get() = catalog().getValue("git_pull_continue")
    val git_pull_dirty_confirm: String get() = catalog().getValue("git_pull_dirty_confirm")
    val git_push: String get() = catalog().getValue("git_push")
    val git_push_keep_master: String get() = catalog().getValue("git_push_keep_master")
    val git_push_master_confirm_desc: String get() = catalog().getValue("git_push_master_confirm_desc")
    val git_push_master_confirm_title: String get() = catalog().getValue("git_push_master_confirm_title")
    val git_push_rename_to_main: String get() = catalog().getValue("git_push_rename_to_main")
    val git_recent_commit: String get() = catalog().getValue("git_recent_commit")
    val git_refresh: String get() = catalog().getValue("git_refresh")
    val git_remote: String get() = catalog().getValue("git_remote")
    val git_remote_url: String get() = catalog().getValue("git_remote_url")
    val git_rename_branch: String get() = catalog().getValue("git_rename_branch")
    val git_rename_to: String get() = catalog().getValue("git_rename_to")
    val git_repo_not_repo: String get() = catalog().getValue("git_repo_not_repo")
    val git_repo_selector_title: String get() = catalog().getValue("git_repo_selector_title")
    val git_repo_url_title: String get() = catalog().getValue("git_repo_url_title")
    val git_reset_confirm: String get() = catalog().getValue("git_reset_confirm")
    val git_reset_mixed: String get() = catalog().getValue("git_reset_mixed")
    val git_reset_soft: String get() = catalog().getValue("git_reset_soft")
    val git_reset_to_commit: String get() = catalog().getValue("git_reset_to_commit")
    val git_restore_file_confirm: String get() = catalog().getValue("git_restore_file_confirm")
    val git_revert_all_confirm: String get() = catalog().getValue("git_revert_all_confirm")
    val git_revert_confirm_head: String get() = catalog().getValue("git_revert_confirm_head")
    val git_revert_confirm_worktree: String get() = catalog().getValue("git_revert_confirm_worktree")
    val git_search_commits_hint: String get() = catalog().getValue("git_search_commits_hint")
    val git_stage: String get() = catalog().getValue("git_stage")
    val git_stage_all: String get() = catalog().getValue("git_stage_all")
    val git_staged_count: String get() = catalog().getValue("git_staged_count")
    val git_staged_label: String get() = catalog().getValue("git_staged_label")
    val git_stash_apply: String get() = catalog().getValue("git_stash_apply")
    val git_stash_clear: String get() = catalog().getValue("git_stash_clear")
    val git_stash_clear_confirm: String get() = catalog().getValue("git_stash_clear_confirm")
    val git_stash_count: String get() = catalog().getValue("git_stash_count")
    val git_stash_drop: String get() = catalog().getValue("git_stash_drop")
    val git_stash_drop_confirm: String get() = catalog().getValue("git_stash_drop_confirm")
    val git_stash_empty: String get() = catalog().getValue("git_stash_empty")
    val git_stash_include_untracked: String get() = catalog().getValue("git_stash_include_untracked")
    val git_stash_message_hint: String get() = catalog().getValue("git_stash_message_hint")
    val git_stash_pop: String get() = catalog().getValue("git_stash_pop")
    val git_stash_title: String get() = catalog().getValue("git_stash_title")
    val git_status_clean: String get() = catalog().getValue("git_status_clean")
    val git_switch_branch: String get() = catalog().getValue("git_switch_branch")
    val git_switch_confirm: String get() = catalog().getValue("git_switch_confirm")
    val git_switch_repo: String get() = catalog().getValue("git_switch_repo")
    val git_tab_branches: String get() = catalog().getValue("git_tab_branches")
    val git_tab_commits: String get() = catalog().getValue("git_tab_commits")
    val git_tab_credentials: String get() = catalog().getValue("git_tab_credentials")
    val git_tab_identity: String get() = catalog().getValue("git_tab_identity")
    val git_tab_stash: String get() = catalog().getValue("git_tab_stash")
    val git_tab_status: String get() = catalog().getValue("git_tab_status")
    val git_tag_name: String get() = catalog().getValue("git_tag_name")
    val git_tags: String get() = catalog().getValue("git_tags")
    val git_title: String get() = catalog().getValue("git_title")
    val git_toast_action_failed: String get() = catalog().getValue("git_toast_action_failed")
    val git_toast_action_refresh_failed: String get() = catalog().getValue("git_toast_action_refresh_failed")
    val git_toast_action_success: String get() = catalog().getValue("git_toast_action_success")
    val git_toast_added_to_gitignore: String get() = catalog().getValue("git_toast_added_to_gitignore")
    val git_toast_checkout_failed: String get() = catalog().getValue("git_toast_checkout_failed")
    val git_toast_checkout_success: String get() = catalog().getValue("git_toast_checkout_success")
    val git_toast_diff_failed: String get() = catalog().getValue("git_toast_diff_failed")
    val git_toast_generate_commit_failed: String get() = catalog().getValue("git_toast_generate_commit_failed")
    val git_toast_load_branches_failed: String get() = catalog().getValue("git_toast_load_branches_failed")
    val git_toast_load_more_failed: String get() = catalog().getValue("git_toast_load_more_failed")
    val git_toast_no_diff_for_commit: String get() = catalog().getValue("git_toast_no_diff_for_commit")
    val git_toast_no_remote_pull: String get() = catalog().getValue("git_toast_no_remote_pull")
    val git_toast_no_remote_push: String get() = catalog().getValue("git_toast_no_remote_push")
    val git_toast_not_repo: String get() = catalog().getValue("git_toast_not_repo")
    val git_toast_path_copied: String get() = catalog().getValue("git_toast_path_copied")
    val git_toast_refresh_failed: String get() = catalog().getValue("git_toast_refresh_failed")
    val git_toast_switch_repo_failed: String get() = catalog().getValue("git_toast_switch_repo_failed")
    val git_tracking_branch: String get() = catalog().getValue("git_tracking_branch")
    val git_unstage: String get() = catalog().getValue("git_unstage")
    val git_unstage_all_confirm: String get() = catalog().getValue("git_unstage_all_confirm")
    val git_untracked_count: String get() = catalog().getValue("git_untracked_count")
    val git_untracked_label: String get() = catalog().getValue("git_untracked_label")
    val image_gen_no_models: String get() = catalog().getValue("image_gen_no_models")
    val language_follow_system: String get() = catalog().getValue("language_follow_system")
    val log_clear_filter: String get() = catalog().getValue("log_clear_filter")
    val log_copied: String get() = catalog().getValue("log_copied")
    val log_copy: String get() = catalog().getValue("log_copy")
    val log_copy_line: String get() = catalog().getValue("log_copy_line")
    val log_copy_menu_title: String get() = catalog().getValue("log_copy_menu_title")
    val log_copy_message: String get() = catalog().getValue("log_copy_message")
    val log_copy_with_stack: String get() = catalog().getValue("log_copy_with_stack")
    val log_file_location: String get() = catalog().getValue("log_file_location")
    val log_files: String get() = catalog().getValue("log_files")
    val log_follow_latest: String get() = catalog().getValue("log_follow_latest")
    val log_level: String get() = catalog().getValue("log_level")
    val log_level_desc: String get() = catalog().getValue("log_level_desc")
    val log_mcp_prefix: String get() = catalog().getValue("log_mcp_prefix")
    val log_no_match: String get() = catalog().getValue("log_no_match")
    val log_search_hint: String get() = catalog().getValue("log_search_hint")
    val log_search_results: String get() = catalog().getValue("log_search_results")
    val log_show_last_lines: String get() = catalog().getValue("log_show_last_lines")
    val log_show_lines: String get() = catalog().getValue("log_show_lines")
    val mcp_add: String get() = catalog().getValue("mcp_add")
    val mcp_add_arg: String get() = catalog().getValue("mcp_add_arg")
    val mcp_add_env_var: String get() = catalog().getValue("mcp_add_env_var")
    val mcp_add_from_json: String get() = catalog().getValue("mcp_add_from_json")
    val mcp_add_from_json_desc: String get() = catalog().getValue("mcp_add_from_json_desc")
    val mcp_add_header: String get() = catalog().getValue("mcp_add_header")
    val mcp_add_manual: String get() = catalog().getValue("mcp_add_manual")
    val mcp_add_manual_desc: String get() = catalog().getValue("mcp_add_manual_desc")
    val mcp_add_sheet_title: String get() = catalog().getValue("mcp_add_sheet_title")
    val mcp_arg_hint: String get() = catalog().getValue("mcp_arg_hint")
    val mcp_arg_value: String get() = catalog().getValue("mcp_arg_value")
    val mcp_args: String get() = catalog().getValue("mcp_args")
    val mcp_auth_advanced: String get() = catalog().getValue("mcp_auth_advanced")
    val mcp_auth_cancel: String get() = catalog().getValue("mcp_auth_cancel")
    val mcp_auth_client_id: String get() = catalog().getValue("mcp_auth_client_id")
    val mcp_auth_client_id_hint: String get() = catalog().getValue("mcp_auth_client_id_hint")
    val mcp_auth_desc: String get() = catalog().getValue("mcp_auth_desc")
    val mcp_auth_enable: String get() = catalog().getValue("mcp_auth_enable")
    val mcp_auth_failed: String get() = catalog().getValue("mcp_auth_failed")
    val mcp_auth_in_progress: String get() = catalog().getValue("mcp_auth_in_progress")
    val mcp_auth_in_progress_hint: String get() = catalog().getValue("mcp_auth_in_progress_hint")
    val mcp_auth_login: String get() = catalog().getValue("mcp_auth_login")
    val mcp_auth_logout: String get() = catalog().getValue("mcp_auth_logout")
    val mcp_auth_reauthorize: String get() = catalog().getValue("mcp_auth_reauthorize")
    val mcp_auth_save_first: String get() = catalog().getValue("mcp_auth_save_first")
    val mcp_auth_server: String get() = catalog().getValue("mcp_auth_server")
    val mcp_auth_server_hint: String get() = catalog().getValue("mcp_auth_server_hint")
    val mcp_auth_status_authorized: String get() = catalog().getValue("mcp_auth_status_authorized")
    val mcp_auth_status_authorizing: String get() = catalog().getValue("mcp_auth_status_authorizing")
    val mcp_auth_status_label: String get() = catalog().getValue("mcp_auth_status_label")
    val mcp_auth_status_unauthorized: String get() = catalog().getValue("mcp_auth_status_unauthorized")
    val mcp_auth_success: String get() = catalog().getValue("mcp_auth_success")
    val mcp_basic_settings: String get() = catalog().getValue("mcp_basic_settings")
    val mcp_batch_delete_confirm: String get() = catalog().getValue("mcp_batch_delete_confirm")
    val mcp_batch_delete_title: String get() = catalog().getValue("mcp_batch_delete_title")
    val mcp_command: String get() = catalog().getValue("mcp_command")
    val mcp_command_hint: String get() = catalog().getValue("mcp_command_hint")
    val mcp_connected: String get() = catalog().getValue("mcp_connected")
    val mcp_connecting: String get() = catalog().getValue("mcp_connecting")
    val mcp_connection_failed: String get() = catalog().getValue("mcp_connection_failed")
    val mcp_custom_headers: String get() = catalog().getValue("mcp_custom_headers")
    val mcp_details: String get() = catalog().getValue("mcp_details")
    val mcp_disabled: String get() = catalog().getValue("mcp_disabled")
    val mcp_edit: String get() = catalog().getValue("mcp_edit")
    val mcp_empty: String get() = catalog().getValue("mcp_empty")
    val mcp_empty_hint: String get() = catalog().getValue("mcp_empty_hint")
    val mcp_enabled: String get() = catalog().getValue("mcp_enabled")
    val mcp_env_name: String get() = catalog().getValue("mcp_env_name")
    val mcp_env_name_hint: String get() = catalog().getValue("mcp_env_name_hint")
    val mcp_env_value: String get() = catalog().getValue("mcp_env_value")
    val mcp_env_value_hint: String get() = catalog().getValue("mcp_env_value_hint")
    val mcp_env_vars: String get() = catalog().getValue("mcp_env_vars")
    val mcp_header_name: String get() = catalog().getValue("mcp_header_name")
    val mcp_header_name_hint: String get() = catalog().getValue("mcp_header_name_hint")
    val mcp_header_value: String get() = catalog().getValue("mcp_header_value")
    val mcp_header_value_hint: String get() = catalog().getValue("mcp_header_value_hint")
    val mcp_json_import_action: String get() = catalog().getValue("mcp_json_import_action")
    val mcp_json_import_empty: String get() = catalog().getValue("mcp_json_import_empty")
    val mcp_json_import_hint: String get() = catalog().getValue("mcp_json_import_hint")
    val mcp_json_import_title: String get() = catalog().getValue("mcp_json_import_title")
    val mcp_local_stdio: String get() = catalog().getValue("mcp_local_stdio")
    val mcp_name_hint: String get() = catalog().getValue("mcp_name_hint")
    val mcp_name_invalid: String get() = catalog().getValue("mcp_name_invalid")
    val mcp_needs_auth: String get() = catalog().getValue("mcp_needs_auth")
    val mcp_no_args: String get() = catalog().getValue("mcp_no_args")
    val mcp_no_custom_headers: String get() = catalog().getValue("mcp_no_custom_headers")
    val mcp_no_description: String get() = catalog().getValue("mcp_no_description")
    val mcp_no_env_vars: String get() = catalog().getValue("mcp_no_env_vars")
    val mcp_no_tools_hint: String get() = catalog().getValue("mcp_no_tools_hint")
    val mcp_not_connected: String get() = catalog().getValue("mcp_not_connected")
    val mcp_refresh_tools: String get() = catalog().getValue("mcp_refresh_tools")
    val mcp_remote_http: String get() = catalog().getValue("mcp_remote_http")
    val mcp_save_first_hint: String get() = catalog().getValue("mcp_save_first_hint")
    val mcp_scope_global: String get() = catalog().getValue("mcp_scope_global")
    val mcp_scope_label: String get() = catalog().getValue("mcp_scope_label")
    val mcp_scope_project: String get() = catalog().getValue("mcp_scope_project")
    val mcp_search_empty: String get() = catalog().getValue("mcp_search_empty")
    val mcp_search_placeholder: String get() = catalog().getValue("mcp_search_placeholder")
    val mcp_server_url: String get() = catalog().getValue("mcp_server_url")
    val mcp_server_url_hint: String get() = catalog().getValue("mcp_server_url_hint")
    val mcp_tools_count: String get() = catalog().getValue("mcp_tools_count")
    val mcp_tools_disable_all: String get() = catalog().getValue("mcp_tools_disable_all")
    val mcp_tools_enable_all: String get() = catalog().getValue("mcp_tools_enable_all")
    val mcp_tools_search: String get() = catalog().getValue("mcp_tools_search")
    val mcp_tools_search_empty: String get() = catalog().getValue("mcp_tools_search_empty")
    val mcp_tools_summary: String get() = catalog().getValue("mcp_tools_summary")
    val mcp_transport_type: String get() = catalog().getValue("mcp_transport_type")
    val mcp_type_stdio: String get() = catalog().getValue("mcp_type_stdio")
    val mcp_view_logs: String get() = catalog().getValue("mcp_view_logs")
    val network_error_connect: String get() = catalog().getValue("network_error_connect")
    val network_error_dns: String get() = catalog().getValue("network_error_dns")
    val network_error_ssl: String get() = catalog().getValue("network_error_ssl")
    val network_error_timeout: String get() = catalog().getValue("network_error_timeout")
    val notification_agent_live_title_multi: String get() = catalog().getValue("notification_agent_live_title_multi")
    val notification_approval_action_allow: String get() = catalog().getValue("notification_approval_action_allow")
    val notification_approval_action_approve_plan: String get() = catalog().getValue("notification_approval_action_approve_plan")
    val notification_approval_action_reject: String get() = catalog().getValue("notification_approval_action_reject")
    val notification_approval_label_permission: String get() = catalog().getValue("notification_approval_label_permission")
    val notification_approval_label_plan: String get() = catalog().getValue("notification_approval_label_plan")
    val notification_approval_label_question: String get() = catalog().getValue("notification_approval_label_question")
    val notification_approval_multi_title: String get() = catalog().getValue("notification_approval_multi_title")
    val notification_approval_needed: String get() = catalog().getValue("notification_approval_needed")
    val notification_approval_title: String get() = catalog().getValue("notification_approval_title")
    val notification_channel_agent_approval: String get() = catalog().getValue("notification_channel_agent_approval")
    val notification_channel_agent_approval_desc: String get() = catalog().getValue("notification_channel_agent_approval_desc")
    val notification_channel_agent_complete: String get() = catalog().getValue("notification_channel_agent_complete")
    val notification_channel_agent_complete_desc: String get() = catalog().getValue("notification_channel_agent_complete_desc")
    val notification_channel_agent_live: String get() = catalog().getValue("notification_channel_agent_live")
    val notification_channel_agent_live_desc: String get() = catalog().getValue("notification_channel_agent_live_desc")
    val notification_channel_terminal_service: String get() = catalog().getValue("notification_channel_terminal_service")
    val notification_channel_terminal_service_desc: String get() = catalog().getValue("notification_channel_terminal_service_desc")
    val notification_keepalive_enabled: String get() = catalog().getValue("notification_keepalive_enabled")
    val notification_keepalive_persistent_sessions: String get() = catalog().getValue("notification_keepalive_persistent_sessions")
    val notification_keepalive_running_sessions: String get() = catalog().getValue("notification_keepalive_running_sessions")
    val onboarding_choose_model_in_sheet_desc: String get() = catalog().getValue("onboarding_choose_model_in_sheet_desc")
    val onboarding_choose_model_in_sheet_title: String get() = catalog().getValue("onboarding_choose_model_in_sheet_title")
    val onboarding_config_provider_desc: String get() = catalog().getValue("onboarding_config_provider_desc")
    val onboarding_config_provider_title: String get() = catalog().getValue("onboarding_config_provider_title")
    val onboarding_done: String get() = catalog().getValue("onboarding_done")
    val onboarding_enter_settings_desc: String get() = catalog().getValue("onboarding_enter_settings_desc")
    val onboarding_enter_settings_title: String get() = catalog().getValue("onboarding_enter_settings_title")
    val onboarding_next: String get() = catalog().getValue("onboarding_next")
    val onboarding_open_model_picker_desc: String get() = catalog().getValue("onboarding_open_model_picker_desc")
    val onboarding_open_model_picker_title: String get() = catalog().getValue("onboarding_open_model_picker_title")
    val onboarding_open_sidebar_desc: String get() = catalog().getValue("onboarding_open_sidebar_desc")
    val onboarding_open_sidebar_title: String get() = catalog().getValue("onboarding_open_sidebar_title")
    val onboarding_provider_add_desc: String get() = catalog().getValue("onboarding_provider_add_desc")
    val onboarding_provider_add_title: String get() = catalog().getValue("onboarding_provider_add_title")
    val onboarding_provider_config_info_desc: String get() = catalog().getValue("onboarding_provider_config_info_desc")
    val onboarding_provider_config_info_title: String get() = catalog().getValue("onboarding_provider_config_info_title")
    val onboarding_provider_fetch_models_desc: String get() = catalog().getValue("onboarding_provider_fetch_models_desc")
    val onboarding_provider_fetch_models_title: String get() = catalog().getValue("onboarding_provider_fetch_models_title")
    val onboarding_provider_select_fetched_model_desc: String get() = catalog().getValue("onboarding_provider_select_fetched_model_desc")
    val onboarding_provider_select_fetched_model_title: String get() = catalog().getValue("onboarding_provider_select_fetched_model_title")
    val onboarding_send_message_desc: String get() = catalog().getValue("onboarding_send_message_desc")
    val onboarding_send_message_title: String get() = catalog().getValue("onboarding_send_message_title")
    val onboarding_skip: String get() = catalog().getValue("onboarding_skip")
    val onboarding_step_badge: String get() = catalog().getValue("onboarding_step_badge")
    val perm_current_project: String get() = catalog().getValue("perm_current_project")
    val perm_current_project_none: String get() = catalog().getValue("perm_current_project_none")
    val perm_deny: String get() = catalog().getValue("perm_deny")
    val perm_disable_safety_interception: String get() = catalog().getValue("perm_disable_safety_interception")
    val perm_disable_safety_interception_desc: String get() = catalog().getValue("perm_disable_safety_interception_desc")
    val perm_entire_tool: String get() = catalog().getValue("perm_entire_tool")
    val perm_global: String get() = catalog().getValue("perm_global")
    val perm_no_global_rules: String get() = catalog().getValue("perm_no_global_rules")
    val perm_no_project_rules: String get() = catalog().getValue("perm_no_project_rules")
    val perm_promote_to_global: String get() = catalog().getValue("perm_promote_to_global")
    val perm_rules_short: String get() = catalog().getValue("perm_rules_short")
    val perm_safety_confirm_action: String get() = catalog().getValue("perm_safety_confirm_action")
    val perm_safety_confirm_action_ready: String get() = catalog().getValue("perm_safety_confirm_action_ready")
    val perm_safety_confirm_message: String get() = catalog().getValue("perm_safety_confirm_message")
    val perm_safety_confirm_title: String get() = catalog().getValue("perm_safety_confirm_title")
    val perm_safety_group: String get() = catalog().getValue("perm_safety_group")
    val plan_mode_already_active: String get() = catalog().getValue("plan_mode_already_active")
    val plan_mode_approved_auto: String get() = catalog().getValue("plan_mode_approved_auto")
    val plan_mode_approved_build: String get() = catalog().getValue("plan_mode_approved_build")
    val plan_mode_enter_action: String get() = catalog().getValue("plan_mode_enter_action")
    val plan_mode_entered: String get() = catalog().getValue("plan_mode_entered")
    val plan_mode_exit_action: String get() = catalog().getValue("plan_mode_exit_action")
    val plan_mode_exited: String get() = catalog().getValue("plan_mode_exited")
    val plan_mode_external_auto: String get() = catalog().getValue("plan_mode_external_auto")
    val plan_mode_external_build: String get() = catalog().getValue("plan_mode_external_build")
    val plan_mode_goal_active: String get() = catalog().getValue("plan_mode_goal_active")
    val plan_mode_invalid_action: String get() = catalog().getValue("plan_mode_invalid_action")
    val plan_mode_not_active: String get() = catalog().getValue("plan_mode_not_active")
    val plan_mode_reason_unspecified: String get() = catalog().getValue("plan_mode_reason_unspecified")
    val plan_mode_refinement_requested: String get() = catalog().getValue("plan_mode_refinement_requested")
    val plan_mode_request_details: String get() = catalog().getValue("plan_mode_request_details")
    val plan_mode_request_summary: String get() = catalog().getValue("plan_mode_request_summary")
    val plan_mode_request_title: String get() = catalog().getValue("plan_mode_request_title")
    val plan_mode_session_missing: String get() = catalog().getValue("plan_mode_session_missing")
    val plan_mode_session_required: String get() = catalog().getValue("plan_mode_session_required")
    val prompts_add_prompt: String get() = catalog().getValue("prompts_add_prompt")
    val prompts_batch_delete_confirm: String get() = catalog().getValue("prompts_batch_delete_confirm")
    val prompts_batch_delete_title: String get() = catalog().getValue("prompts_batch_delete_title")
    val prompts_disable_builtin: String get() = catalog().getValue("prompts_disable_builtin")
    val prompts_disable_builtin_hint: String get() = catalog().getValue("prompts_disable_builtin_hint")
    val prompts_editor_edit: String get() = catalog().getValue("prompts_editor_edit")
    val prompts_editor_new: String get() = catalog().getValue("prompts_editor_new")
    val prompts_empty: String get() = catalog().getValue("prompts_empty")
    val prompts_field_basic: String get() = catalog().getValue("prompts_field_basic")
    val prompts_field_content: String get() = catalog().getValue("prompts_field_content")
    val prompts_field_content_placeholder: String get() = catalog().getValue("prompts_field_content_placeholder")
    val prompts_field_number: String get() = catalog().getValue("prompts_field_number")
    val prompts_field_number_placeholder: String get() = catalog().getValue("prompts_field_number_placeholder")
    val prompts_field_summary: String get() = catalog().getValue("prompts_field_summary")
    val prompts_field_summary_placeholder: String get() = catalog().getValue("prompts_field_summary_placeholder")
    val prompts_field_title: String get() = catalog().getValue("prompts_field_title")
    val prompts_field_title_placeholder: String get() = catalog().getValue("prompts_field_title_placeholder")
    val prompts_help: String get() = catalog().getValue("prompts_help")
    val prompts_reset_confirm: String get() = catalog().getValue("prompts_reset_confirm")
    val prompts_reset_custom: String get() = catalog().getValue("prompts_reset_custom")
    val prompts_sort_long_press: String get() = catalog().getValue("prompts_sort_long_press")
    val prompts_source_builtin: String get() = catalog().getValue("prompts_source_builtin")
    val prompts_source_global: String get() = catalog().getValue("prompts_source_global")
    val prompts_source_local: String get() = catalog().getValue("prompts_source_local")
    val prompts_source_project: String get() = catalog().getValue("prompts_source_project")
    val prompts_title: String get() = catalog().getValue("prompts_title")
    val provider_add: String get() = catalog().getValue("provider_add")
    val provider_add_all_models: String get() = catalog().getValue("provider_add_all_models")
    val provider_add_all_models_confirm: String get() = catalog().getValue("provider_add_all_models_confirm")
    val provider_add_all_models_title: String get() = catalog().getValue("provider_add_all_models_title")
    val provider_add_model: String get() = catalog().getValue("provider_add_model")
    val provider_add_sheet_title: String get() = catalog().getValue("provider_add_sheet_title")
    val provider_api_key: String get() = catalog().getValue("provider_api_key")
    val provider_api_key_required: String get() = catalog().getValue("provider_api_key_required")
    val provider_base_url: String get() = catalog().getValue("provider_base_url")
    val provider_brand_doubao: String get() = catalog().getValue("provider_brand_doubao")
    val provider_brand_hunyuan: String get() = catalog().getValue("provider_brand_hunyuan")
    val provider_brand_siliconflow: String get() = catalog().getValue("provider_brand_siliconflow")
    val provider_brand_tongyi_qianwen: String get() = catalog().getValue("provider_brand_tongyi_qianwen")
    val provider_brand_zhipu: String get() = catalog().getValue("provider_brand_zhipu")
    val provider_cache_anthropic_subtitle: String get() = catalog().getValue("provider_cache_anthropic_subtitle")
    val provider_cache_anthropic_title: String get() = catalog().getValue("provider_cache_anthropic_title")
    val provider_cache_openai_chat_subtitle: String get() = catalog().getValue("provider_cache_openai_chat_subtitle")
    val provider_cache_openai_chat_title: String get() = catalog().getValue("provider_cache_openai_chat_title")
    val provider_config: String get() = catalog().getValue("provider_config")
    val provider_custom: String get() = catalog().getValue("provider_custom")
    val provider_dashboard_clear_script: String get() = catalog().getValue("provider_dashboard_clear_script")
    val provider_dashboard_loading: String get() = catalog().getValue("provider_dashboard_loading")
    val provider_dashboard_no_scripts: String get() = catalog().getValue("provider_dashboard_no_scripts")
    val provider_dashboard_preview_title: String get() = catalog().getValue("provider_dashboard_preview_title")
    val provider_dashboard_raw_output: String get() = catalog().getValue("provider_dashboard_raw_output")
    val provider_dashboard_run_test: String get() = catalog().getValue("provider_dashboard_run_test")
    val provider_dashboard_script: String get() = catalog().getValue("provider_dashboard_script")
    val provider_dashboard_script_desc: String get() = catalog().getValue("provider_dashboard_script_desc")
    val provider_dashboard_script_placeholder: String get() = catalog().getValue("provider_dashboard_script_placeholder")
    val provider_dashboard_select_script: String get() = catalog().getValue("provider_dashboard_select_script")
    val provider_dashboard_select_title: String get() = catalog().getValue("provider_dashboard_select_title")
    val provider_dashboard_test_btn: String get() = catalog().getValue("provider_dashboard_test_btn")
    val provider_dashboard_test_failed: String get() = catalog().getValue("provider_dashboard_test_failed")
    val provider_edit: String get() = catalog().getValue("provider_edit")
    val provider_edit_model: String get() = catalog().getValue("provider_edit_model")
    val provider_fetch_failed: String get() = catalog().getValue("provider_fetch_failed")
    val provider_fetch_http_error: String get() = catalog().getValue("provider_fetch_http_error")
    val provider_fetch_missing_fields: String get() = catalog().getValue("provider_fetch_missing_fields")
    val provider_fetch_missing_fields_detail: String get() = catalog().getValue("provider_fetch_missing_fields_detail")
    val provider_fetch_models: String get() = catalog().getValue("provider_fetch_models")
    val provider_fetch_success: String get() = catalog().getValue("provider_fetch_success")
    val provider_filter_models_hint: String get() = catalog().getValue("provider_filter_models_hint")
    val provider_full_url: String get() = catalog().getValue("provider_full_url")
    val provider_full_url_desc: String get() = catalog().getValue("provider_full_url_desc")
    val provider_headers_add: String get() = catalog().getValue("provider_headers_add")
    val provider_headers_count: String get() = catalog().getValue("provider_headers_count")
    val provider_headers_desc: String get() = catalog().getValue("provider_headers_desc")
    val provider_headers_empty: String get() = catalog().getValue("provider_headers_empty")
    val provider_headers_manage: String get() = catalog().getValue("provider_headers_manage")
    val provider_headers_name: String get() = catalog().getValue("provider_headers_name")
    val provider_headers_name_hint: String get() = catalog().getValue("provider_headers_name_hint")
    val provider_headers_remove: String get() = catalog().getValue("provider_headers_remove")
    val provider_headers_title: String get() = catalog().getValue("provider_headers_title")
    val provider_headers_value: String get() = catalog().getValue("provider_headers_value")
    val provider_headers_value_hint: String get() = catalog().getValue("provider_headers_value_hint")
    val provider_hide_api_key: String get() = catalog().getValue("provider_hide_api_key")
    val provider_interactions_api: String get() = catalog().getValue("provider_interactions_api")
    val provider_model_already_added: String get() = catalog().getValue("provider_model_already_added")
    val provider_model_cap_image_output: String get() = catalog().getValue("provider_model_cap_image_output")
    val provider_model_cap_vision: String get() = catalog().getValue("provider_model_cap_vision")
    val provider_model_capabilities: String get() = catalog().getValue("provider_model_capabilities")
    val provider_model_capability_reasoning: String get() = catalog().getValue("provider_model_capability_reasoning")
    val provider_model_capability_tools: String get() = catalog().getValue("provider_model_capability_tools")
    val provider_model_context_input: String get() = catalog().getValue("provider_model_context_input")
    val provider_model_context_output: String get() = catalog().getValue("provider_model_context_output")
    val provider_model_context_total: String get() = catalog().getValue("provider_model_context_total")
    val provider_model_icon: String get() = catalog().getValue("provider_model_icon")
    val provider_model_name: String get() = catalog().getValue("provider_model_name")
    val provider_model_price_cache_read: String get() = catalog().getValue("provider_model_price_cache_read")
    val provider_model_price_cache_write: String get() = catalog().getValue("provider_model_price_cache_write")
    val provider_model_price_input: String get() = catalog().getValue("provider_model_price_input")
    val provider_model_price_output: String get() = catalog().getValue("provider_model_price_output")
    val provider_model_reasoning_can_disable: String get() = catalog().getValue("provider_model_reasoning_can_disable")
    val provider_model_reasoning_hint: String get() = catalog().getValue("provider_model_reasoning_hint")
    val provider_model_section_price: String get() = catalog().getValue("provider_model_section_price")
    val provider_model_section_reasoning: String get() = catalog().getValue("provider_model_section_reasoning")
    val provider_model_window_invalid: String get() = catalog().getValue("provider_model_window_invalid")
    val provider_models_aligned_removed: String get() = catalog().getValue("provider_models_aligned_removed")
    val provider_models_batch_delete: String get() = catalog().getValue("provider_models_batch_delete")
    val provider_models_batch_delete_confirm: String get() = catalog().getValue("provider_models_batch_delete_confirm")
    val provider_models_batch_delete_title: String get() = catalog().getValue("provider_models_batch_delete_title")
    val provider_models_batch_test: String get() = catalog().getValue("provider_models_batch_test")
    val provider_models_count: String get() = catalog().getValue("provider_models_count")
    val provider_models_count_tag: String get() = catalog().getValue("provider_models_count_tag")
    val provider_models_deselect_all: String get() = catalog().getValue("provider_models_deselect_all")
    val provider_models_select: String get() = catalog().getValue("provider_models_select")
    val provider_models_select_all: String get() = catalog().getValue("provider_models_select_all")
    val provider_models_selected_count: String get() = catalog().getValue("provider_models_selected_count")
    val provider_multi_key_add: String get() = catalog().getValue("provider_multi_key_add")
    val provider_multi_key_cooldown: String get() = catalog().getValue("provider_multi_key_cooldown")
    val provider_multi_key_cooldown_desc: String get() = catalog().getValue("provider_multi_key_cooldown_desc")
    val provider_multi_key_cooldown_off: String get() = catalog().getValue("provider_multi_key_cooldown_off")
    val provider_multi_key_cooldown_value: String get() = catalog().getValue("provider_multi_key_cooldown_value")
    val provider_multi_key_empty: String get() = catalog().getValue("provider_multi_key_empty")
    val provider_multi_key_failover: String get() = catalog().getValue("provider_multi_key_failover")
    val provider_multi_key_item: String get() = catalog().getValue("provider_multi_key_item")
    val provider_multi_key_list: String get() = catalog().getValue("provider_multi_key_list")
    val provider_multi_key_manage: String get() = catalog().getValue("provider_multi_key_manage")
    val provider_multi_key_status_codes: String get() = catalog().getValue("provider_multi_key_status_codes")
    val provider_multi_key_status_codes_hint: String get() = catalog().getValue("provider_multi_key_status_codes_hint")
    val provider_multi_key_strategy: String get() = catalog().getValue("provider_multi_key_strategy")
    val provider_multi_key_strategy_round_robin: String get() = catalog().getValue("provider_multi_key_strategy_round_robin")
    val provider_multi_key_strategy_round_robin_desc: String get() = catalog().getValue("provider_multi_key_strategy_round_robin_desc")
    val provider_multi_key_strategy_sequential: String get() = catalog().getValue("provider_multi_key_strategy_sequential")
    val provider_multi_key_strategy_sequential_desc: String get() = catalog().getValue("provider_multi_key_strategy_sequential_desc")
    val provider_multi_key_subtitle: String get() = catalog().getValue("provider_multi_key_subtitle")
    val provider_multi_key_summary: String get() = catalog().getValue("provider_multi_key_summary")
    val provider_multi_key_title: String get() = catalog().getValue("provider_multi_key_title")
    val provider_multi_key_weight: String get() = catalog().getValue("provider_multi_key_weight")
    val provider_multi_key_weight_hint: String get() = catalog().getValue("provider_multi_key_weight_hint")
    val provider_new: String get() = catalog().getValue("provider_new")
    val provider_no_matching_models: String get() = catalog().getValue("provider_no_matching_models")
    val provider_no_models: String get() = catalog().getValue("provider_no_models")
    val provider_no_models_hint: String get() = catalog().getValue("provider_no_models_hint")
    val provider_please_wait: String get() = catalog().getValue("provider_please_wait")
    val provider_preset_empty: String get() = catalog().getValue("provider_preset_empty")
    val provider_preset_loading: String get() = catalog().getValue("provider_preset_loading")
    val provider_proxy_enable: String get() = catalog().getValue("provider_proxy_enable")
    val provider_proxy_enable_desc: String get() = catalog().getValue("provider_proxy_enable_desc")
    val provider_proxy_off: String get() = catalog().getValue("provider_proxy_off")
    val provider_proxy_section: String get() = catalog().getValue("provider_proxy_section")
    val provider_register_now: String get() = catalog().getValue("provider_register_now")
    val provider_response_api: String get() = catalog().getValue("provider_response_api")
    val provider_script_param_add: String get() = catalog().getValue("provider_script_param_add")
    val provider_script_param_key: String get() = catalog().getValue("provider_script_param_key")
    val provider_script_param_key_hint: String get() = catalog().getValue("provider_script_param_key_hint")
    val provider_script_param_manage: String get() = catalog().getValue("provider_script_param_manage")
    val provider_script_param_remove: String get() = catalog().getValue("provider_script_param_remove")
    val provider_script_param_value: String get() = catalog().getValue("provider_script_param_value")
    val provider_script_param_value_hint: String get() = catalog().getValue("provider_script_param_value_hint")
    val provider_script_params_count: String get() = catalog().getValue("provider_script_params_count")
    val provider_script_params_desc: String get() = catalog().getValue("provider_script_params_desc")
    val provider_script_params_empty: String get() = catalog().getValue("provider_script_params_empty")
    val provider_script_params_title: String get() = catalog().getValue("provider_script_params_title")
    val provider_section_basic: String get() = catalog().getValue("provider_section_basic")
    val provider_section_dashboard: String get() = catalog().getValue("provider_section_dashboard")
    val provider_section_options: String get() = catalog().getValue("provider_section_options")
    val provider_section_type: String get() = catalog().getValue("provider_section_type")
    val provider_show_api_key: String get() = catalog().getValue("provider_show_api_key")
    val provider_sort_long_press: String get() = catalog().getValue("provider_sort_long_press")
    val provider_test: String get() = catalog().getValue("provider_test")
    val provider_test_detail_title: String get() = catalog().getValue("provider_test_detail_title")
    val provider_test_error_detail: String get() = catalog().getValue("provider_test_error_detail")
    val provider_test_req_body: String get() = catalog().getValue("provider_test_req_body")
    val provider_test_req_headers: String get() = catalog().getValue("provider_test_req_headers")
    val provider_test_req_url: String get() = catalog().getValue("provider_test_req_url")
    val provider_test_resp_body: String get() = catalog().getValue("provider_test_resp_body")
    val provider_test_resp_headers: String get() = catalog().getValue("provider_test_resp_headers")
    val provider_test_success: String get() = catalog().getValue("provider_test_success")
    val providers_batch_delete_confirm: String get() = catalog().getValue("providers_batch_delete_confirm")
    val providers_batch_delete_title: String get() = catalog().getValue("providers_batch_delete_title")
    val providers_empty: String get() = catalog().getValue("providers_empty")
    val proxy_auth: String get() = catalog().getValue("proxy_auth")
    val proxy_enable: String get() = catalog().getValue("proxy_enable")
    val proxy_enable_subtitle: String get() = catalog().getValue("proxy_enable_subtitle")
    val proxy_host: String get() = catalog().getValue("proxy_host")
    val proxy_host_placeholder: String get() = catalog().getValue("proxy_host_placeholder")
    val proxy_no_proxy: String get() = catalog().getValue("proxy_no_proxy")
    val proxy_no_proxy_hint: String get() = catalog().getValue("proxy_no_proxy_hint")
    val proxy_password: String get() = catalog().getValue("proxy_password")
    val proxy_port: String get() = catalog().getValue("proxy_port")
    val proxy_port_placeholder: String get() = catalog().getValue("proxy_port_placeholder")
    val proxy_server: String get() = catalog().getValue("proxy_server")
    val proxy_test: String get() = catalog().getValue("proxy_test")
    val proxy_test_connect_failed: String get() = catalog().getValue("proxy_test_connect_failed")
    val proxy_test_http_error: String get() = catalog().getValue("proxy_test_http_error")
    val proxy_test_invalid_config: String get() = catalog().getValue("proxy_test_invalid_config")
    val proxy_test_ok: String get() = catalog().getValue("proxy_test_ok")
    val proxy_test_title: String get() = catalog().getValue("proxy_test_title")
    val proxy_test_url_label: String get() = catalog().getValue("proxy_test_url_label")
    val proxy_test_url_placeholder: String get() = catalog().getValue("proxy_test_url_placeholder")
    val proxy_testing: String get() = catalog().getValue("proxy_testing")
    val proxy_title: String get() = catalog().getValue("proxy_title")
    val proxy_type: String get() = catalog().getValue("proxy_type")
    val proxy_type_http: String get() = catalog().getValue("proxy_type_http")
    val proxy_type_socks5: String get() = catalog().getValue("proxy_type_socks5")
    val proxy_username: String get() = catalog().getValue("proxy_username")
    val remote_add_channel_first: String get() = catalog().getValue("remote_add_channel_first")
    val remote_add_connection: String get() = catalog().getValue("remote_add_connection")
    val remote_add_workspace: String get() = catalog().getValue("remote_add_workspace")
    val remote_auth_key: String get() = catalog().getValue("remote_auth_key")
    val remote_auth_password: String get() = catalog().getValue("remote_auth_password")
    val remote_auto_connect_and_sync: String get() = catalog().getValue("remote_auto_connect_and_sync")
    val remote_auto_connect_on_start: String get() = catalog().getValue("remote_auto_connect_on_start")
    val remote_browse_dir: String get() = catalog().getValue("remote_browse_dir")
    val remote_clear_index_done: String get() = catalog().getValue("remote_clear_index_done")
    val remote_confirm_select: String get() = catalog().getValue("remote_confirm_select")
    val remote_connect_failed: String get() = catalog().getValue("remote_connect_failed")
    val remote_connect_success: String get() = catalog().getValue("remote_connect_success")
    val remote_connection_name_hint: String get() = catalog().getValue("remote_connection_name_hint")
    val remote_connection_sort_long_press: String get() = catalog().getValue("remote_connection_sort_long_press")
    val remote_current_path: String get() = catalog().getValue("remote_current_path")
    val remote_delete_connection_confirm: String get() = catalog().getValue("remote_delete_connection_confirm")
    val remote_delete_mount_confirm: String get() = catalog().getValue("remote_delete_mount_confirm")
    val remote_download_all_failed: String get() = catalog().getValue("remote_download_all_failed")
    val remote_download_all_success: String get() = catalog().getValue("remote_download_all_success")
    val remote_edit_connection: String get() = catalog().getValue("remote_edit_connection")
    val remote_edit_workspace: String get() = catalog().getValue("remote_edit_workspace")
    val remote_go_add: String get() = catalog().getValue("remote_go_add")
    val remote_hint_title: String get() = catalog().getValue("remote_hint_title")
    val remote_host_address: String get() = catalog().getValue("remote_host_address")
    val remote_key_passphrase: String get() = catalog().getValue("remote_key_passphrase")
    val remote_link_channel: String get() = catalog().getValue("remote_link_channel")
    val remote_load_failed: String get() = catalog().getValue("remote_load_failed")
    val remote_login_key: String get() = catalog().getValue("remote_login_key")
    val remote_map_to_local: String get() = catalog().getValue("remote_map_to_local")
    val remote_mount_already_exists: String get() = catalog().getValue("remote_mount_already_exists")
    val remote_no_connections: String get() = catalog().getValue("remote_no_connections")
    val remote_no_connections_desc: String get() = catalog().getValue("remote_no_connections_desc")
    val remote_no_keys_hint: String get() = catalog().getValue("remote_no_keys_hint")
    val remote_no_local_workspace: String get() = catalog().getValue("remote_no_local_workspace")
    val remote_no_workspaces: String get() = catalog().getValue("remote_no_workspaces")
    val remote_no_workspaces_desc: String get() = catalog().getValue("remote_no_workspaces_desc")
    val remote_parent_dir: String get() = catalog().getValue("remote_parent_dir")
    val remote_password: String get() = catalog().getValue("remote_password")
    val remote_port: String get() = catalog().getValue("remote_port")
    val remote_select_channel: String get() = catalog().getValue("remote_select_channel")
    val remote_select_key: String get() = catalog().getValue("remote_select_key")
    val remote_select_local_workspace: String get() = catalog().getValue("remote_select_local_workspace")
    val remote_select_remote_dir: String get() = catalog().getValue("remote_select_remote_dir")
    val remote_tab_connections: String get() = catalog().getValue("remote_tab_connections")
    val remote_tab_ftp: String get() = catalog().getValue("remote_tab_ftp")
    val remote_tab_mounts: String get() = catalog().getValue("remote_tab_mounts")
    val remote_tab_sync: String get() = catalog().getValue("remote_tab_sync")
    val remote_target_dir: String get() = catalog().getValue("remote_target_dir")
    val remote_test_connection: String get() = catalog().getValue("remote_test_connection")
    val remote_toggle_password: String get() = catalog().getValue("remote_toggle_password")
    val remote_unknown_error: String get() = catalog().getValue("remote_unknown_error")
    val remote_upload_failed: String get() = catalog().getValue("remote_upload_failed")
    val remote_upload_full_failed: String get() = catalog().getValue("remote_upload_full_failed")
    val remote_upload_full_success: String get() = catalog().getValue("remote_upload_full_success")
    val remote_upload_success: String get() = catalog().getValue("remote_upload_success")
    val remote_workspace_title: String get() = catalog().getValue("remote_workspace_title")
    val retry_error_connection_refused: String get() = catalog().getValue("retry_error_connection_refused")
    val retry_error_connection_reset: String get() = catalog().getValue("retry_error_connection_reset")
    val retry_error_dns_failed: String get() = catalog().getValue("retry_error_dns_failed")
    val retry_error_network: String get() = catalog().getValue("retry_error_network")
    val retry_error_rate_limit: String get() = catalog().getValue("retry_error_rate_limit")
    val retry_error_server: String get() = catalog().getValue("retry_error_server")
    val retry_error_server_overloaded: String get() = catalog().getValue("retry_error_server_overloaded")
    val retry_error_ssl: String get() = catalog().getValue("retry_error_ssl")
    val retry_error_timeout: String get() = catalog().getValue("retry_error_timeout")
    val retry_error_unknown: String get() = catalog().getValue("retry_error_unknown")
    val retry_error_with_code: String get() = catalog().getValue("retry_error_with_code")
    val session_group_last_30_days: String get() = catalog().getValue("session_group_last_30_days")
    val session_group_last_7_days: String get() = catalog().getValue("session_group_last_7_days")
    val session_group_month_format: String get() = catalog().getValue("session_group_month_format")
    val session_group_pinned: String get() = catalog().getValue("session_group_pinned")
    val session_group_today: String get() = catalog().getValue("session_group_today")
    val session_group_yesterday: String get() = catalog().getValue("session_group_yesterday")
    val settings_about: String get() = catalog().getValue("settings_about")
    val settings_add_mcp_server: String get() = catalog().getValue("settings_add_mcp_server")
    val settings_add_provider: String get() = catalog().getValue("settings_add_provider")
    val settings_agent_sound_subtitle: String get() = catalog().getValue("settings_agent_sound_subtitle")
    val settings_agent_sound_title: String get() = catalog().getValue("settings_agent_sound_title")
    val settings_app_permissions: String get() = catalog().getValue("settings_app_permissions")
    val settings_approval_notification_subtitle: String get() = catalog().getValue("settings_approval_notification_subtitle")
    val settings_approval_notification_title: String get() = catalog().getValue("settings_approval_notification_title")
    val settings_auto_remove_stale_models: String get() = catalog().getValue("settings_auto_remove_stale_models")
    val settings_auto_remove_stale_models_desc: String get() = catalog().getValue("settings_auto_remove_stale_models_desc")
    val settings_autostart_subtitle: String get() = catalog().getValue("settings_autostart_subtitle")
    val settings_autostart_title: String get() = catalog().getValue("settings_autostart_title")
    val settings_background_image: String get() = catalog().getValue("settings_background_image")
    val settings_background_image_alpha: String get() = catalog().getValue("settings_background_image_alpha")
    val settings_background_image_none: String get() = catalog().getValue("settings_background_image_none")
    val settings_background_image_pick: String get() = catalog().getValue("settings_background_image_pick")
    val settings_background_image_remove: String get() = catalog().getValue("settings_background_image_remove")
    val settings_backup: String get() = catalog().getValue("settings_backup")
    val settings_category_ai: String get() = catalog().getValue("settings_category_ai")
    val settings_category_background: String get() = catalog().getValue("settings_category_background")
    val settings_category_data: String get() = catalog().getValue("settings_category_data")
    val settings_category_environment: String get() = catalog().getValue("settings_category_environment")
    val settings_category_general: String get() = catalog().getValue("settings_category_general")
    val settings_category_help: String get() = catalog().getValue("settings_category_help")
    val settings_category_permissions: String get() = catalog().getValue("settings_category_permissions")
    val settings_category_system_permissions: String get() = catalog().getValue("settings_category_system_permissions")
    val settings_compaction_follow_chat: String get() = catalog().getValue("settings_compaction_follow_chat")
    val settings_compaction_model: String get() = catalog().getValue("settings_compaction_model")
    val settings_compaction_threshold: String get() = catalog().getValue("settings_compaction_threshold")
    val settings_compaction_threshold_desc: String get() = catalog().getValue("settings_compaction_threshold_desc")
    val settings_compaction_threshold_input_hint: String get() = catalog().getValue("settings_compaction_threshold_input_hint")
    val settings_container: String get() = catalog().getValue("settings_container")
    val settings_default_models: String get() = catalog().getValue("settings_default_models")
    val settings_delete_external_workspace_sessions: String get() = catalog().getValue("settings_delete_external_workspace_sessions")
    val settings_delete_external_workspace_sessions_desc: String get() = catalog().getValue("settings_delete_external_workspace_sessions_desc")
    val settings_enter_to_send: String get() = catalog().getValue("settings_enter_to_send")
    val settings_enter_to_send_desc: String get() = catalog().getValue("settings_enter_to_send_desc")
    val settings_first_byte_timeout: String get() = catalog().getValue("settings_first_byte_timeout")
    val settings_first_byte_timeout_desc: String get() = catalog().getValue("settings_first_byte_timeout_desc")
    val settings_general: String get() = catalog().getValue("settings_general")
    val settings_general_goal: String get() = catalog().getValue("settings_general_goal")
    val settings_general_input: String get() = catalog().getValue("settings_general_input")
    val settings_general_models: String get() = catalog().getValue("settings_general_models")
    val settings_general_network: String get() = catalog().getValue("settings_general_network")
    val settings_general_session: String get() = catalog().getValue("settings_general_session")
    val settings_general_tools: String get() = catalog().getValue("settings_general_tools")
    val settings_goal_max_tokens: String get() = catalog().getValue("settings_goal_max_tokens")
    val settings_goal_max_tokens_desc: String get() = catalog().getValue("settings_goal_max_tokens_desc")
    val settings_goal_max_tokens_input_hint: String get() = catalog().getValue("settings_goal_max_tokens_input_hint")
    val settings_goal_max_turns: String get() = catalog().getValue("settings_goal_max_turns")
    val settings_goal_max_turns_desc: String get() = catalog().getValue("settings_goal_max_turns_desc")
    val settings_goal_max_turns_input_hint: String get() = catalog().getValue("settings_goal_max_turns_input_hint")
    val settings_image_gen_model: String get() = catalog().getValue("settings_image_gen_model")
    val settings_image_gen_unconfigured: String get() = catalog().getValue("settings_image_gen_unconfigured")
    val settings_keepalive_subtitle: String get() = catalog().getValue("settings_keepalive_subtitle")
    val settings_keepalive_title: String get() = catalog().getValue("settings_keepalive_title")
    val settings_language: String get() = catalog().getValue("settings_language")
    val settings_live_notification_subtitle: String get() = catalog().getValue("settings_live_notification_subtitle")
    val settings_live_notification_title: String get() = catalog().getValue("settings_live_notification_title")
    val settings_log: String get() = catalog().getValue("settings_log")
    val settings_log_no_files: String get() = catalog().getValue("settings_log_no_files")
    val settings_log_read_failed: String get() = catalog().getValue("settings_log_read_failed")
    val settings_max_network_retries: String get() = catalog().getValue("settings_max_network_retries")
    val settings_max_network_retries_desc: String get() = catalog().getValue("settings_max_network_retries_desc")
    val settings_mb_value: String get() = catalog().getValue("settings_mb_value")
    val settings_mcp: String get() = catalog().getValue("settings_mcp")
    val settings_models_fetch_failed: String get() = catalog().getValue("settings_models_fetch_failed")
    val settings_network_guide_brand_huawei: String get() = catalog().getValue("settings_network_guide_brand_huawei")
    val settings_network_guide_brand_oppo: String get() = catalog().getValue("settings_network_guide_brand_oppo")
    val settings_network_guide_brand_samsung: String get() = catalog().getValue("settings_network_guide_brand_samsung")
    val settings_network_guide_brand_vivo: String get() = catalog().getValue("settings_network_guide_brand_vivo")
    val settings_network_guide_brand_xiaomi: String get() = catalog().getValue("settings_network_guide_brand_xiaomi")
    val settings_network_guide_intro: String get() = catalog().getValue("settings_network_guide_intro")
    val settings_network_guide_more: String get() = catalog().getValue("settings_network_guide_more")
    val settings_network_guide_step_battery: String get() = catalog().getValue("settings_network_guide_step_battery")
    val settings_network_guide_step_keepalive: String get() = catalog().getValue("settings_network_guide_step_keepalive")
    val settings_network_guide_step_lock: String get() = catalog().getValue("settings_network_guide_step_lock")
    val settings_network_guide_subtitle: String get() = catalog().getValue("settings_network_guide_subtitle")
    val settings_network_guide_title: String get() = catalog().getValue("settings_network_guide_title")
    val settings_percent_value: String get() = catalog().getValue("settings_percent_value")
    val settings_permission_apk_install: String get() = catalog().getValue("settings_permission_apk_install")
    val settings_permission_apk_install_desc: String get() = catalog().getValue("settings_permission_apk_install_desc")
    val settings_permission_battery: String get() = catalog().getValue("settings_permission_battery")
    val settings_permission_battery_desc: String get() = catalog().getValue("settings_permission_battery_desc")
    val settings_permission_denied: String get() = catalog().getValue("settings_permission_denied")
    val settings_permission_granted: String get() = catalog().getValue("settings_permission_granted")
    val settings_permission_notification_denied_desc: String get() = catalog().getValue("settings_permission_notification_denied_desc")
    val settings_permission_notification_title: String get() = catalog().getValue("settings_permission_notification_title")
    val settings_permission_storage: String get() = catalog().getValue("settings_permission_storage")
    val settings_permission_storage_denied_desc: String get() = catalog().getValue("settings_permission_storage_denied_desc")
    val settings_permission_storage_desc: String get() = catalog().getValue("settings_permission_storage_desc")
    val settings_permissions: String get() = catalog().getValue("settings_permissions")
    val settings_provider_editor: String get() = catalog().getValue("settings_provider_editor")
    val settings_providers: String get() = catalog().getValue("settings_providers")
    val settings_reconnect: String get() = catalog().getValue("settings_reconnect")
    val settings_refresh_logs: String get() = catalog().getValue("settings_refresh_logs")
    val settings_remote_servers: String get() = catalog().getValue("settings_remote_servers")
    val settings_rerun_onboarding: String get() = catalog().getValue("settings_rerun_onboarding")
    val settings_retries_count: String get() = catalog().getValue("settings_retries_count")
    val settings_retries_input_hint: String get() = catalog().getValue("settings_retries_input_hint")
    val settings_screen_on_subtitle: String get() = catalog().getValue("settings_screen_on_subtitle")
    val settings_screen_on_title: String get() = catalog().getValue("settings_screen_on_title")
    val settings_select_section_hint: String get() = catalog().getValue("settings_select_section_hint")
    val settings_sendfile_max_size: String get() = catalog().getValue("settings_sendfile_max_size")
    val settings_sendfile_max_size_desc: String get() = catalog().getValue("settings_sendfile_max_size_desc")
    val settings_sendfile_max_size_input_hint: String get() = catalog().getValue("settings_sendfile_max_size_input_hint")
    val settings_shizuku: String get() = catalog().getValue("settings_shizuku")
    val settings_shizuku_hint_denied: String get() = catalog().getValue("settings_shizuku_hint_denied")
    val settings_shizuku_hint_not_installed: String get() = catalog().getValue("settings_shizuku_hint_not_installed")
    val settings_shizuku_hint_not_running: String get() = catalog().getValue("settings_shizuku_hint_not_running")
    val settings_shizuku_hint_ready: String get() = catalog().getValue("settings_shizuku_hint_ready")
    val settings_shizuku_status_denied: String get() = catalog().getValue("settings_shizuku_status_denied")
    val settings_shizuku_status_not_installed: String get() = catalog().getValue("settings_shizuku_status_not_installed")
    val settings_shizuku_status_not_running: String get() = catalog().getValue("settings_shizuku_status_not_running")
    val settings_shizuku_status_ready: String get() = catalog().getValue("settings_shizuku_status_ready")
    val settings_skills: String get() = catalog().getValue("settings_skills")
    val settings_startup_session: String get() = catalog().getValue("settings_startup_session")
    val settings_startup_session_new: String get() = catalog().getValue("settings_startup_session_new")
    val settings_startup_session_new_desc: String get() = catalog().getValue("settings_startup_session_new_desc")
    val settings_startup_session_recent: String get() = catalog().getValue("settings_startup_session_recent")
    val settings_startup_session_recent_desc: String get() = catalog().getValue("settings_startup_session_recent_desc")
    val settings_storage: String get() = catalog().getValue("settings_storage")
    val settings_stream_idle_timeout: String get() = catalog().getValue("settings_stream_idle_timeout")
    val settings_stream_idle_timeout_desc: String get() = catalog().getValue("settings_stream_idle_timeout_desc")
    val settings_subagent_tier_light: String get() = catalog().getValue("settings_subagent_tier_light")
    val settings_subagent_tier_standard: String get() = catalog().getValue("settings_subagent_tier_standard")
    val settings_subagent_tier_strong: String get() = catalog().getValue("settings_subagent_tier_strong")
    val settings_subagent_tier_unconfigured: String get() = catalog().getValue("settings_subagent_tier_unconfigured")
    val settings_subagent_tiers_header: String get() = catalog().getValue("settings_subagent_tiers_header")
    val settings_subagents: String get() = catalog().getValue("settings_subagents")
    val settings_theme_title: String get() = catalog().getValue("settings_theme_title")
    val settings_timeout_input_hint: String get() = catalog().getValue("settings_timeout_input_hint")
    val settings_timeout_seconds: String get() = catalog().getValue("settings_timeout_seconds")
    val settings_timeout_unlimited: String get() = catalog().getValue("settings_timeout_unlimited")
    val settings_title: String get() = catalog().getValue("settings_title")
    val settings_title_follow_chat: String get() = catalog().getValue("settings_title_follow_chat")
    val settings_title_model: String get() = catalog().getValue("settings_title_model")
    val settings_token_stats_cache_hit_rate: String get() = catalog().getValue("settings_token_stats_cache_hit_rate")
    val settings_token_stats_cache_read_write: String get() = catalog().getValue("settings_token_stats_cache_read_write")
    val settings_token_stats_calls_suffix: String get() = catalog().getValue("settings_token_stats_calls_suffix")
    val settings_token_stats_calls_suffix2: String get() = catalog().getValue("settings_token_stats_calls_suffix2")
    val settings_token_stats_col_cache_hit_rate: String get() = catalog().getValue("settings_token_stats_col_cache_hit_rate")
    val settings_token_stats_col_cached_read: String get() = catalog().getValue("settings_token_stats_col_cached_read")
    val settings_token_stats_col_cached_write: String get() = catalog().getValue("settings_token_stats_col_cached_write")
    val settings_token_stats_col_cost: String get() = catalog().getValue("settings_token_stats_col_cost")
    val settings_token_stats_col_duration: String get() = catalog().getValue("settings_token_stats_col_duration")
    val settings_token_stats_col_input: String get() = catalog().getValue("settings_token_stats_col_input")
    val settings_token_stats_col_model: String get() = catalog().getValue("settings_token_stats_col_model")
    val settings_token_stats_col_output: String get() = catalog().getValue("settings_token_stats_col_output")
    val settings_token_stats_col_provider: String get() = catalog().getValue("settings_token_stats_col_provider")
    val settings_token_stats_col_reasoning_effort: String get() = catalog().getValue("settings_token_stats_col_reasoning_effort")
    val settings_token_stats_col_reasoning_wire: String get() = catalog().getValue("settings_token_stats_col_reasoning_wire")
    val settings_token_stats_col_status: String get() = catalog().getValue("settings_token_stats_col_status")
    val settings_token_stats_col_time: String get() = catalog().getValue("settings_token_stats_col_time")
    val settings_token_stats_col_ttfb: String get() = catalog().getValue("settings_token_stats_col_ttfb")
    val settings_token_stats_cost: String get() = catalog().getValue("settings_token_stats_cost")
    val settings_token_stats_filter_all_models: String get() = catalog().getValue("settings_token_stats_filter_all_models")
    val settings_token_stats_filter_all_providers: String get() = catalog().getValue("settings_token_stats_filter_all_providers")
    val settings_token_stats_filter_clear: String get() = catalog().getValue("settings_token_stats_filter_clear")
    val settings_token_stats_filter_model: String get() = catalog().getValue("settings_token_stats_filter_model")
    val settings_token_stats_filter_provider: String get() = catalog().getValue("settings_token_stats_filter_provider")
    val settings_token_stats_legend_cached: String get() = catalog().getValue("settings_token_stats_legend_cached")
    val settings_token_stats_legend_input: String get() = catalog().getValue("settings_token_stats_legend_input")
    val settings_token_stats_legend_output: String get() = catalog().getValue("settings_token_stats_legend_output")
    val settings_token_stats_model: String get() = catalog().getValue("settings_token_stats_model")
    val settings_token_stats_no_data: String get() = catalog().getValue("settings_token_stats_no_data")
    val settings_token_stats_page: String get() = catalog().getValue("settings_token_stats_page")
    val settings_token_stats_period_30d: String get() = catalog().getValue("settings_token_stats_period_30d")
    val settings_token_stats_period_7d: String get() = catalog().getValue("settings_token_stats_period_7d")
    val settings_token_stats_period_all: String get() = catalog().getValue("settings_token_stats_period_all")
    val settings_token_stats_period_today: String get() = catalog().getValue("settings_token_stats_period_today")
    val settings_token_stats_provider: String get() = catalog().getValue("settings_token_stats_provider")
    val settings_token_stats_provider_keys: String get() = catalog().getValue("settings_token_stats_provider_keys")
    val settings_token_stats_recent_calls: String get() = catalog().getValue("settings_token_stats_recent_calls")
    val settings_token_stats_requests: String get() = catalog().getValue("settings_token_stats_requests")
    val settings_token_stats_requests_unit: String get() = catalog().getValue("settings_token_stats_requests_unit")
    val settings_token_stats_reset: String get() = catalog().getValue("settings_token_stats_reset")
    val settings_token_stats_reset_confirm: String get() = catalog().getValue("settings_token_stats_reset_confirm")
    val settings_token_stats_reset_title: String get() = catalog().getValue("settings_token_stats_reset_title")
    val settings_token_stats_retries: String get() = catalog().getValue("settings_token_stats_retries")
    val settings_token_stats_success_rate: String get() = catalog().getValue("settings_token_stats_success_rate")
    val settings_token_stats_title: String get() = catalog().getValue("settings_token_stats_title")
    val settings_token_stats_total: String get() = catalog().getValue("settings_token_stats_total")
    val settings_token_stats_trend: String get() = catalog().getValue("settings_token_stats_trend")
    val settings_token_stats_unknown: String get() = catalog().getValue("settings_token_stats_unknown")
    val settings_tokens_value: String get() = catalog().getValue("settings_tokens_value")
    val settings_turns_value: String get() = catalog().getValue("settings_turns_value")
    val settings_user_guide: String get() = catalog().getValue("settings_user_guide")
    val settings_vision_follow_chat: String get() = catalog().getValue("settings_vision_follow_chat")
    val settings_vision_model: String get() = catalog().getValue("settings_vision_model")
    val skills_add: String get() = catalog().getValue("skills_add")
    val skills_add_from_file: String get() = catalog().getValue("skills_add_from_file")
    val skills_add_from_file_desc: String get() = catalog().getValue("skills_add_from_file_desc")
    val skills_add_from_zip: String get() = catalog().getValue("skills_add_from_zip")
    val skills_add_from_zip_desc: String get() = catalog().getValue("skills_add_from_zip_desc")
    val skills_add_manual: String get() = catalog().getValue("skills_add_manual")
    val skills_add_manual_desc: String get() = catalog().getValue("skills_add_manual_desc")
    val skills_add_sheet_title: String get() = catalog().getValue("skills_add_sheet_title")
    val skills_batch_delete_confirm: String get() = catalog().getValue("skills_batch_delete_confirm")
    val skills_batch_delete_title: String get() = catalog().getValue("skills_batch_delete_title")
    val skills_delete_confirm_message: String get() = catalog().getValue("skills_delete_confirm_message")
    val skills_delete_confirm_title: String get() = catalog().getValue("skills_delete_confirm_title")
    val skills_description: String get() = catalog().getValue("skills_description")
    val skills_description_hint: String get() = catalog().getValue("skills_description_hint")
    val skills_edit: String get() = catalog().getValue("skills_edit")
    val skills_editor_basic: String get() = catalog().getValue("skills_editor_basic")
    val skills_editor_edit: String get() = catalog().getValue("skills_editor_edit")
    val skills_editor_error_empty_instructions: String get() = catalog().getValue("skills_editor_error_empty_instructions")
    val skills_editor_error_invalid_name: String get() = catalog().getValue("skills_editor_error_invalid_name")
    val skills_editor_error_io: String get() = catalog().getValue("skills_editor_error_io")
    val skills_editor_error_name_conflict: String get() = catalog().getValue("skills_editor_error_name_conflict")
    val skills_editor_new: String get() = catalog().getValue("skills_editor_new")
    val skills_editor_scope: String get() = catalog().getValue("skills_editor_scope")
    val skills_empty: String get() = catalog().getValue("skills_empty")
    val skills_empty_hint: String get() = catalog().getValue("skills_empty_hint")
    val skills_enable: String get() = catalog().getValue("skills_enable")
    val skills_import_error_empty_content: String get() = catalog().getValue("skills_import_error_empty_content")
    val skills_import_error_invalid_archive: String get() = catalog().getValue("skills_import_error_invalid_archive")
    val skills_import_error_invalid_name: String get() = catalog().getValue("skills_import_error_invalid_name")
    val skills_import_error_io: String get() = catalog().getValue("skills_import_error_io")
    val skills_import_error_name_conflict: String get() = catalog().getValue("skills_import_error_name_conflict")
    val skills_import_error_no_skill: String get() = catalog().getValue("skills_import_error_no_skill")
    val skills_import_error_unsupported_file: String get() = catalog().getValue("skills_import_error_unsupported_file")
    val skills_import_failure_line: String get() = catalog().getValue("skills_import_failure_line")
    val skills_import_partial: String get() = catalog().getValue("skills_import_partial")
    val skills_import_result_title: String get() = catalog().getValue("skills_import_result_title")
    val skills_import_running: String get() = catalog().getValue("skills_import_running")
    val skills_import_success: String get() = catalog().getValue("skills_import_success")
    val skills_instructions: String get() = catalog().getValue("skills_instructions")
    val skills_instructions_hint: String get() = catalog().getValue("skills_instructions_hint")
    val skills_name_hint: String get() = catalog().getValue("skills_name_hint")
    val skills_no_global_skills: String get() = catalog().getValue("skills_no_global_skills")
    val skills_no_project_skills: String get() = catalog().getValue("skills_no_project_skills")
    val skills_scope_global: String get() = catalog().getValue("skills_scope_global")
    val skills_scope_project: String get() = catalog().getValue("skills_scope_project")
    val skills_summary: String get() = catalog().getValue("skills_summary")
    val slash_command_compress_desc: String get() = catalog().getValue("slash_command_compress_desc")
    val slash_command_goal_desc: String get() = catalog().getValue("slash_command_goal_desc")
    val slash_command_init_desc: String get() = catalog().getValue("slash_command_init_desc")
    val slash_command_usage_desc: String get() = catalog().getValue("slash_command_usage_desc")
    val ssh_host_key_changed_title: String get() = catalog().getValue("ssh_host_key_changed_title")
    val ssh_host_key_confirm_title: String get() = catalog().getValue("ssh_host_key_confirm_title")
    val ssh_host_key_detail: String get() = catalog().getValue("ssh_host_key_detail")
    val ssh_host_key_empty: String get() = catalog().getValue("ssh_host_key_empty")
    val ssh_host_key_fingerprint_value: String get() = catalog().getValue("ssh_host_key_fingerprint_value")
    val ssh_host_key_reject: String get() = catalog().getValue("ssh_host_key_reject")
    val ssh_host_key_remove: String get() = catalog().getValue("ssh_host_key_remove")
    val ssh_host_key_settings_desc: String get() = catalog().getValue("ssh_host_key_settings_desc")
    val ssh_host_key_settings_title: String get() = catalog().getValue("ssh_host_key_settings_title")
    val ssh_host_key_tab_fingerprints: String get() = catalog().getValue("ssh_host_key_tab_fingerprints")
    val ssh_host_key_tab_keys: String get() = catalog().getValue("ssh_host_key_tab_keys")
    val ssh_host_key_trust: String get() = catalog().getValue("ssh_host_key_trust")
    val ssh_login_key_add: String get() = catalog().getValue("ssh_login_key_add")
    val ssh_login_key_add_from_file: String get() = catalog().getValue("ssh_login_key_add_from_file")
    val ssh_login_key_add_from_file_desc: String get() = catalog().getValue("ssh_login_key_add_from_file_desc")
    val ssh_login_key_add_from_paste: String get() = catalog().getValue("ssh_login_key_add_from_paste")
    val ssh_login_key_add_from_paste_desc: String get() = catalog().getValue("ssh_login_key_add_from_paste_desc")
    val ssh_login_key_add_method_title: String get() = catalog().getValue("ssh_login_key_add_method_title")
    val ssh_login_key_content_label: String get() = catalog().getValue("ssh_login_key_content_label")
    val ssh_login_key_edit_title: String get() = catalog().getValue("ssh_login_key_edit_title")
    val ssh_login_key_empty: String get() = catalog().getValue("ssh_login_key_empty")
    val ssh_login_key_encrypted: String get() = catalog().getValue("ssh_login_key_encrypted")
    val ssh_login_key_fingerprint_label: String get() = catalog().getValue("ssh_login_key_fingerprint_label")
    val ssh_login_key_name_label: String get() = catalog().getValue("ssh_login_key_name_label")
    val ssh_login_key_passphrase_placeholder: String get() = catalog().getValue("ssh_login_key_passphrase_placeholder")
    val ssh_login_key_paste_content_placeholder: String get() = catalog().getValue("ssh_login_key_paste_content_placeholder")
    val ssh_login_key_paste_invalid: String get() = catalog().getValue("ssh_login_key_paste_invalid")
    val ssh_login_key_paste_name_placeholder: String get() = catalog().getValue("ssh_login_key_paste_name_placeholder")
    val ssh_login_key_paste_title: String get() = catalog().getValue("ssh_login_key_paste_title")
    val ssh_login_key_toggle_content: String get() = catalog().getValue("ssh_login_key_toggle_content")
    val status_connected: String get() = catalog().getValue("status_connected")
    val status_connection_failed: String get() = catalog().getValue("status_connection_failed")
    val status_disconnected: String get() = catalog().getValue("status_disconnected")
    val status_reconnecting: String get() = catalog().getValue("status_reconnecting")
    val storage_breakdown_note: String get() = catalog().getValue("storage_breakdown_note")
    val storage_category_ai_config: String get() = catalog().getValue("storage_category_ai_config")
    val storage_category_apk: String get() = catalog().getValue("storage_category_apk")
    val storage_category_caches: String get() = catalog().getValue("storage_category_caches")
    val storage_category_chat: String get() = catalog().getValue("storage_category_chat")
    val storage_category_checkpoints: String get() = catalog().getValue("storage_category_checkpoints")
    val storage_category_container: String get() = catalog().getValue("storage_category_container")
    val storage_category_container_images: String get() = catalog().getValue("storage_category_container_images")
    val storage_category_logs: String get() = catalog().getValue("storage_category_logs")
    val storage_category_other: String get() = catalog().getValue("storage_category_other")
    val storage_category_workspaces: String get() = catalog().getValue("storage_category_workspaces")
    val storage_chat_messages: String get() = catalog().getValue("storage_chat_messages")
    val storage_clean: String get() = catalog().getValue("storage_clean")
    val storage_clean_caches: String get() = catalog().getValue("storage_clean_caches")
    val storage_clean_caches_desc: String get() = catalog().getValue("storage_clean_caches_desc")
    val storage_clean_confirm_selected: String get() = catalog().getValue("storage_clean_confirm_selected")
    val storage_clean_confirm_title: String get() = catalog().getValue("storage_clean_confirm_title")
    val storage_clean_logs: String get() = catalog().getValue("storage_clean_logs")
    val storage_clean_logs_desc: String get() = catalog().getValue("storage_clean_logs_desc")
    val storage_clean_scope: String get() = catalog().getValue("storage_clean_scope")
    val storage_clean_selected: String get() = catalog().getValue("storage_clean_selected")
    val storage_clean_tool_output: String get() = catalog().getValue("storage_clean_tool_output")
    val storage_clean_tool_output_desc: String get() = catalog().getValue("storage_clean_tool_output_desc")
    val storage_clean_vision: String get() = catalog().getValue("storage_clean_vision")
    val storage_clean_vision_desc: String get() = catalog().getValue("storage_clean_vision_desc")
    val storage_device_free: String get() = catalog().getValue("storage_device_free")
    val storage_freed: String get() = catalog().getValue("storage_freed")
    val storage_logs_ai: String get() = catalog().getValue("storage_logs_ai")
    val storage_logs_app: String get() = catalog().getValue("storage_logs_app")
    val storage_refresh: String get() = catalog().getValue("storage_refresh")
    val storage_scanning: String get() = catalog().getValue("storage_scanning")
    val storage_section_breakdown: String get() = catalog().getValue("storage_section_breakdown")
    val storage_total_title: String get() = catalog().getValue("storage_total_title")
    val storage_untitled_session: String get() = catalog().getValue("storage_untitled_session")
    val storage_workspace_attachments: String get() = catalog().getValue("storage_workspace_attachments")
    val subagent_add: String get() = catalog().getValue("subagent_add")
    val subagent_all_tools: String get() = catalog().getValue("subagent_all_tools")
    val subagent_card_changed_files: String get() = catalog().getValue("subagent_card_changed_files")
    val subagent_card_collapse: String get() = catalog().getValue("subagent_card_collapse")
    val subagent_card_default_agent: String get() = catalog().getValue("subagent_card_default_agent")
    val subagent_card_expand: String get() = catalog().getValue("subagent_card_expand")
    val subagent_card_open: String get() = catalog().getValue("subagent_card_open")
    val subagent_card_status_completed: String get() = catalog().getValue("subagent_card_status_completed")
    val subagent_card_status_dispatched: String get() = catalog().getValue("subagent_card_status_dispatched")
    val subagent_card_status_failed: String get() = catalog().getValue("subagent_card_status_failed")
    val subagent_card_status_running: String get() = catalog().getValue("subagent_card_status_running")
    val subagent_card_status_stopped: String get() = catalog().getValue("subagent_card_status_stopped")
    val subagent_card_stop: String get() = catalog().getValue("subagent_card_stop")
    val subagent_description_hint: String get() = catalog().getValue("subagent_description_hint")
    val subagent_dispatch_count: String get() = catalog().getValue("subagent_dispatch_count")
    val subagent_dispatch_title: String get() = catalog().getValue("subagent_dispatch_title")
    val subagent_edit: String get() = catalog().getValue("subagent_edit")
    val subagent_editor_basic: String get() = catalog().getValue("subagent_editor_basic")
    val subagent_editor_clear: String get() = catalog().getValue("subagent_editor_clear")
    val subagent_editor_description: String get() = catalog().getValue("subagent_editor_description")
    val subagent_editor_edit: String get() = catalog().getValue("subagent_editor_edit")
    val subagent_editor_effort: String get() = catalog().getValue("subagent_editor_effort")
    val subagent_editor_error_empty_prompt: String get() = catalog().getValue("subagent_editor_error_empty_prompt")
    val subagent_editor_error_invalid_name: String get() = catalog().getValue("subagent_editor_error_invalid_name")
    val subagent_editor_error_io: String get() = catalog().getValue("subagent_editor_error_io")
    val subagent_editor_error_name_conflict: String get() = catalog().getValue("subagent_editor_error_name_conflict")
    val subagent_editor_mode: String get() = catalog().getValue("subagent_editor_mode")
    val subagent_editor_model_group: String get() = catalog().getValue("subagent_editor_model_group")
    val subagent_editor_new: String get() = catalog().getValue("subagent_editor_new")
    val subagent_editor_none: String get() = catalog().getValue("subagent_editor_none")
    val subagent_editor_scope: String get() = catalog().getValue("subagent_editor_scope")
    val subagent_editor_selected_count: String get() = catalog().getValue("subagent_editor_selected_count")
    val subagent_editor_tools_allow: String get() = catalog().getValue("subagent_editor_tools_allow")
    val subagent_editor_tools_allow_hint: String get() = catalog().getValue("subagent_editor_tools_allow_hint")
    val subagent_editor_tools_deny: String get() = catalog().getValue("subagent_editor_tools_deny")
    val subagent_editor_tools_deny_hint: String get() = catalog().getValue("subagent_editor_tools_deny_hint")
    val subagent_editor_tools_search: String get() = catalog().getValue("subagent_editor_tools_search")
    val subagent_enable: String get() = catalog().getValue("subagent_enable")
    val subagent_enable_hint: String get() = catalog().getValue("subagent_enable_hint")
    val subagent_inherit_parent: String get() = catalog().getValue("subagent_inherit_parent")
    val subagent_inject: String get() = catalog().getValue("subagent_inject")
    val subagent_inject_base: String get() = catalog().getValue("subagent_inject_base")
    val subagent_inject_base_hint: String get() = catalog().getValue("subagent_inject_base_hint")
    val subagent_inject_main_rules: String get() = catalog().getValue("subagent_inject_main_rules")
    val subagent_inject_main_rules_hint: String get() = catalog().getValue("subagent_inject_main_rules_hint")
    val subagent_inject_memory: String get() = catalog().getValue("subagent_inject_memory")
    val subagent_inject_memory_hint: String get() = catalog().getValue("subagent_inject_memory_hint")
    val subagent_inject_none: String get() = catalog().getValue("subagent_inject_none")
    val subagent_inject_project_rules: String get() = catalog().getValue("subagent_inject_project_rules")
    val subagent_inject_project_rules_hint: String get() = catalog().getValue("subagent_inject_project_rules_hint")
    val subagent_inject_skills: String get() = catalog().getValue("subagent_inject_skills")
    val subagent_inject_skills_hint: String get() = catalog().getValue("subagent_inject_skills_hint")
    val subagent_model: String get() = catalog().getValue("subagent_model")
    val subagent_name_hint: String get() = catalog().getValue("subagent_name_hint")
    val subagent_no_models: String get() = catalog().getValue("subagent_no_models")
    val subagent_prompt: String get() = catalog().getValue("subagent_prompt")
    val subagent_prompt_hint: String get() = catalog().getValue("subagent_prompt_hint")
    val subagent_scope_global: String get() = catalog().getValue("subagent_scope_global")
    val subagent_scope_project: String get() = catalog().getValue("subagent_scope_project")
    val subagent_source_file: String get() = catalog().getValue("subagent_source_file")
    val subagent_tab_sessions: String get() = catalog().getValue("subagent_tab_sessions")
    val subagent_tools: String get() = catalog().getValue("subagent_tools")
    val subagent_tools_allow: String get() = catalog().getValue("subagent_tools_allow")
    val subagent_tools_deny: String get() = catalog().getValue("subagent_tools_deny")
    val subagents_batch_delete_confirm: String get() = catalog().getValue("subagents_batch_delete_confirm")
    val subagents_batch_delete_title: String get() = catalog().getValue("subagents_batch_delete_title")
    val subagents_delete_confirm_message: String get() = catalog().getValue("subagents_delete_confirm_message")
    val subagents_delete_confirm_title: String get() = catalog().getValue("subagents_delete_confirm_title")
    val subagents_empty: String get() = catalog().getValue("subagents_empty")
    val subagents_empty_hint: String get() = catalog().getValue("subagents_empty_hint")
    val subagents_no_global: String get() = catalog().getValue("subagents_no_global")
    val subagents_no_project: String get() = catalog().getValue("subagents_no_project")
    val sync_batch_size_desc: String get() = catalog().getValue("sync_batch_size_desc")
    val sync_clear_index: String get() = catalog().getValue("sync_clear_index")
    val sync_connect_and_sync: String get() = catalog().getValue("sync_connect_and_sync")
    val sync_disconnect: String get() = catalog().getValue("sync_disconnect")
    val sync_download_all: String get() = catalog().getValue("sync_download_all")
    val sync_follow_gitignore: String get() = catalog().getValue("sync_follow_gitignore")
    val sync_gitignore_desc: String get() = catalog().getValue("sync_gitignore_desc")
    val sync_max_batch_count: String get() = catalog().getValue("sync_max_batch_count")
    val sync_max_batch_size: String get() = catalog().getValue("sync_max_batch_size")
    val sync_path_mapping: String get() = catalog().getValue("sync_path_mapping")
    val sync_save_settings: String get() = catalog().getValue("sync_save_settings")
    val sync_saved: String get() = catalog().getValue("sync_saved")
    val sync_settings_title: String get() = catalog().getValue("sync_settings_title")
    val sync_unknown_connection: String get() = catalog().getValue("sync_unknown_connection")
    val sync_upload_changes: String get() = catalog().getValue("sync_upload_changes")
    val sync_upload_full: String get() = catalog().getValue("sync_upload_full")
    val terminal_cleaning_old_container: String get() = catalog().getValue("terminal_cleaning_old_container")
    val terminal_close_tab: String get() = catalog().getValue("terminal_close_tab")
    val terminal_cursor_bar: String get() = catalog().getValue("terminal_cursor_bar")
    val terminal_cursor_block: String get() = catalog().getValue("terminal_cursor_block")
    val terminal_cursor_style_title: String get() = catalog().getValue("terminal_cursor_style_title")
    val terminal_cursor_underline: String get() = catalog().getValue("terminal_cursor_underline")
    val terminal_extracting_env: String get() = catalog().getValue("terminal_extracting_env")
    val terminal_font_builtin: String get() = catalog().getValue("terminal_font_builtin")
    val terminal_font_default: String get() = catalog().getValue("terminal_font_default")
    val terminal_font_delete: String get() = catalog().getValue("terminal_font_delete")
    val terminal_font_import: String get() = catalog().getValue("terminal_font_import")
    val terminal_font_import_failed: String get() = catalog().getValue("terminal_font_import_failed")
    val terminal_font_size_title: String get() = catalog().getValue("terminal_font_size_title")
    val terminal_font_title: String get() = catalog().getValue("terminal_font_title")
    val terminal_installing_packages: String get() = catalog().getValue("terminal_installing_packages")
    val terminal_no_open_tabs: String get() = catalog().getValue("terminal_no_open_tabs")
    val terminal_prepare_error_unknown: String get() = catalog().getValue("terminal_prepare_error_unknown")
    val terminal_preparing_env_failed: String get() = catalog().getValue("terminal_preparing_env_failed")
    val terminal_preparing_env_first_run: String get() = catalog().getValue("terminal_preparing_env_first_run")
    val terminal_preview_text: String get() = catalog().getValue("terminal_preview_text")
    val terminal_retry: String get() = catalog().getValue("terminal_retry")
    val terminal_settings_title: String get() = catalog().getValue("terminal_settings_title")
    val terminal_start_failed: String get() = catalog().getValue("terminal_start_failed")
    val terminal_theme_dracula: String get() = catalog().getValue("terminal_theme_dracula")
    val terminal_theme_follow_app: String get() = catalog().getValue("terminal_theme_follow_app")
    val terminal_theme_github_dark: String get() = catalog().getValue("terminal_theme_github_dark")
    val terminal_theme_github_light: String get() = catalog().getValue("terminal_theme_github_light")
    val terminal_theme_monokai: String get() = catalog().getValue("terminal_theme_monokai")
    val terminal_theme_one_dark: String get() = catalog().getValue("terminal_theme_one_dark")
    val terminal_theme_termius_dark: String get() = catalog().getValue("terminal_theme_termius_dark")
    val terminal_theme_title: String get() = catalog().getValue("terminal_theme_title")
    val terminal_title: String get() = catalog().getValue("terminal_title")
    val theme_auto: String get() = catalog().getValue("theme_auto")
    val theme_dark: String get() = catalog().getValue("theme_dark")
    val theme_dynamic_color: String get() = catalog().getValue("theme_dynamic_color")
    val theme_dynamic_color_desc: String get() = catalog().getValue("theme_dynamic_color_desc")
    val theme_dynamic_color_unsupported: String get() = catalog().getValue("theme_dynamic_color_unsupported")
    val theme_light: String get() = catalog().getValue("theme_light")
    val theme_mode_section: String get() = catalog().getValue("theme_mode_section")
    val theme_preset_coffee: String get() = catalog().getValue("theme_preset_coffee")
    val theme_preset_default: String get() = catalog().getValue("theme_preset_default")
    val theme_preset_forest: String get() = catalog().getValue("theme_preset_forest")
    val theme_preset_graphite: String get() = catalog().getValue("theme_preset_graphite")
    val theme_preset_section: String get() = catalog().getValue("theme_preset_section")
    val title_no_models: String get() = catalog().getValue("title_no_models")
    val todo_completed: String get() = catalog().getValue("todo_completed")
    val todo_dashboard_all_done: String get() = catalog().getValue("todo_dashboard_all_done")
    val todo_dashboard_progress: String get() = catalog().getValue("todo_dashboard_progress")
    val todo_dashboard_title: String get() = catalog().getValue("todo_dashboard_title")
    val todo_in_progress: String get() = catalog().getValue("todo_in_progress")
    val token_stats_hour_suffix: String get() = catalog().getValue("token_stats_hour_suffix")
    val tool_browser_condition_met: String get() = catalog().getValue("tool_browser_condition_met")
    val tool_browser_screenshot_failed: String get() = catalog().getValue("tool_browser_screenshot_failed")
    val tool_browser_wait_timeout: String get() = catalog().getValue("tool_browser_wait_timeout")
    val tool_changed_files_summary: String get() = catalog().getValue("tool_changed_files_summary")
    val tool_command_rule_denied: String get() = catalog().getValue("tool_command_rule_denied")
    val tool_command_rule_denied_detail: String get() = catalog().getValue("tool_command_rule_denied_detail")
    val tool_command_unanalyzable_reason: String get() = catalog().getValue("tool_command_unanalyzable_reason")
    val tool_expand_remaining: String get() = catalog().getValue("tool_expand_remaining")
    val tool_instruction: String get() = catalog().getValue("tool_instruction")
    val tool_mcp_invalid_name: String get() = catalog().getValue("tool_mcp_invalid_name")
    val tool_mcp_stdio_added: String get() = catalog().getValue("tool_mcp_stdio_added")
    val tool_memory_saved: String get() = catalog().getValue("tool_memory_saved")
    val tool_output_omitted: String get() = catalog().getValue("tool_output_omitted")
    val tool_output_saved_hint: String get() = catalog().getValue("tool_output_saved_hint")
    val tool_output_unsaved_hint: String get() = catalog().getValue("tool_output_unsaved_hint")
    val tool_permission_rule_denied: String get() = catalog().getValue("tool_permission_rule_denied")
    val tool_plan_mode_denied: String get() = catalog().getValue("tool_plan_mode_denied")
    val tool_result: String get() = catalog().getValue("tool_result")
    val tool_shizuku_confirmation_reason: String get() = catalog().getValue("tool_shizuku_confirmation_reason")
    val tool_subagent_message_delivered: String get() = catalog().getValue("tool_subagent_message_delivered")
    val tool_subagent_plan_readonly_warning: String get() = catalog().getValue("tool_subagent_plan_readonly_warning")
    val tool_subagent_provider_unresolved: String get() = catalog().getValue("tool_subagent_provider_unresolved")
    val tool_subagent_tier_fallback: String get() = catalog().getValue("tool_subagent_tier_fallback")
    val tool_subagent_tier_pinned: String get() = catalog().getValue("tool_subagent_tier_pinned")
    val tool_subagent_tier_unavailable: String get() = catalog().getValue("tool_subagent_tier_unavailable")
    val tool_vision_default_multiple: String get() = catalog().getValue("tool_vision_default_multiple")
    val tool_vision_default_single: String get() = catalog().getValue("tool_vision_default_single")
    val tool_vision_empty_response: String get() = catalog().getValue("tool_vision_empty_response")
    val tool_vision_focus: String get() = catalog().getValue("tool_vision_focus")
    val tool_vision_images_attached: String get() = catalog().getValue("tool_vision_images_attached")
    val update_channel_latest: String get() = catalog().getValue("update_channel_latest")
    val update_channel_latest_desc: String get() = catalog().getValue("update_channel_latest_desc")
    val update_channel_stable: String get() = catalog().getValue("update_channel_stable")
    val update_channel_stable_desc: String get() = catalog().getValue("update_channel_stable_desc")
    val vision_no_models: String get() = catalog().getValue("vision_no_models")
    val web_search_results_count: String get() = catalog().getValue("web_search_results_count")
    val web_search_title: String get() = catalog().getValue("web_search_title")
    val web_search_warning: String get() = catalog().getValue("web_search_warning")
    val workbench_split_handle: String get() = catalog().getValue("workbench_split_handle")
    val workspace_add_external: String get() = catalog().getValue("workspace_add_external")
    val workspace_confirm: String get() = catalog().getValue("workspace_confirm")
    val workspace_delete: String get() = catalog().getValue("workspace_delete")
    val workspace_delete_confirm: String get() = catalog().getValue("workspace_delete_confirm")
    val workspace_empty_hint: String get() = catalog().getValue("workspace_empty_hint")
    val workspace_external_added: String get() = catalog().getValue("workspace_external_added")
    val workspace_external_exists: String get() = catalog().getValue("workspace_external_exists")
    val workspace_external_invalid: String get() = catalog().getValue("workspace_external_invalid")
    val workspace_external_remove_confirm: String get() = catalog().getValue("workspace_external_remove_confirm")
    val workspace_external_remove_confirm_sessions: String get() = catalog().getValue("workspace_external_remove_confirm_sessions")
    val workspace_external_unavailable_hint: String get() = catalog().getValue("workspace_external_unavailable_hint")
    val workspace_external_unresolvable: String get() = catalog().getValue("workspace_external_unresolvable")
    val workspace_external_unsupported_path: String get() = catalog().getValue("workspace_external_unsupported_path")
    val workspace_external_unwritable: String get() = catalog().getValue("workspace_external_unwritable")
    val workspace_external_warning_continue: String get() = catalog().getValue("workspace_external_warning_continue")
    val workspace_external_warning_dont_show_again: String get() = catalog().getValue("workspace_external_warning_dont_show_again")
    val workspace_external_warning_message: String get() = catalog().getValue("workspace_external_warning_message")
    val workspace_external_warning_title: String get() = catalog().getValue("workspace_external_warning_title")
    val workspace_name_exists: String get() = catalog().getValue("workspace_name_exists")
    val workspace_name_hint: String get() = catalog().getValue("workspace_name_hint")
    val workspace_new_internal: String get() = catalog().getValue("workspace_new_internal")
    val workspace_new_workspace: String get() = catalog().getValue("workspace_new_workspace")
    val workspace_open: String get() = catalog().getValue("workspace_open")
    val workspace_remote_default_create_failed: String get() = catalog().getValue("workspace_remote_default_create_failed")
    val workspace_remote_root_create_failed: String get() = catalog().getValue("workspace_remote_root_create_failed")
    val workspace_select: String get() = catalog().getValue("workspace_select")
    val workspace_switch: String get() = catalog().getValue("workspace_switch")
    val workspace_switch_confirm: String get() = catalog().getValue("workspace_switch_confirm")
    val workspace_unavailable_current: String get() = catalog().getValue("workspace_unavailable_current")
    val workspace_unavailable_fallback: String get() = catalog().getValue("workspace_unavailable_fallback")

    private val zh: Map<String, String> = mapOf(
        "about_auto_check_update" to "自动检查更新",
        "about_auto_check_update_desc" to "打开 App 时自动检测新版本",
        "about_check_failed" to "检查失败",
        "about_check_update" to "检查更新",
        "about_checking_update" to "正在向 GitHub 查询最新版本…",
        "about_description" to "Android 上的通用 AI Coding Agent · Linux 开发环境 · 支持本地与远程 SSH",
        "about_download" to "前往下载",
        "about_github_repo" to "GitHub 仓库",
        "about_later" to "稍后",
        "about_license" to "开源许可证",
        "about_network_error" to "网络错误",
        "about_new_version_found" to "发现新版本",
        "about_no_changelog" to "暂无更新说明",
        "about_ok" to "确定",
        "about_parse_version_failed" to "无法解析最新版本号",
        "about_up_to_date" to "已经是最新版本",
        "about_up_to_date_detail" to "当前版本 v%1${'s，已是 GitHub 上最新的 Release。}s，已是 GitHub 上最新的 Release。",
        "about_update_apk_ready" to "安装包已就绪，点「安装」完成更新",
        "about_update_background" to "后台继续",
        "about_update_browser" to "浏览器下载",
        "about_update_channel" to "更新通道",
        "about_update_download_failed" to "下载失败",
        "about_update_download_failed_hint" to "可在「设置 → 关于 → 下载镜像源」更换镜像后重试",
        "about_update_download_install" to "下载并安装",
        "about_update_download_size" to "%1${'s / %2$s}s / %2${'s}s",
        "about_update_downloading" to "正在下载 %1${'d%%}d%%",
        "about_update_install" to "安装",
        "about_update_install_failed" to "没有可用的安装程序",
        "about_update_mirror" to "下载镜像源",
        "about_update_mirror_default" to "恢复默认",
        "about_update_mirror_desc" to "加速更新包下载；留空表示直连 GitHub",
        "about_update_mirror_invalid" to "地址需以 http:// 或 https:// 开头",
        "about_update_mirror_preset_cdn" to "gh-proxy CDN（默认）",
        "about_update_mirror_preset_direct" to "直连 GitHub",
        "about_update_mirror_preset_v6" to "gh-proxy 优选节点",
        "about_update_need_permission" to "需先允许「安装未知应用」，授权后回来点「安装」即可",
        "about_update_retry" to "重试",
        "about_update_verify_not_apk" to "下载到的文件不是有效安装包",
        "about_update_verify_not_newer" to "安装包版本不高于当前版本",
        "about_update_verify_package" to "安装包与当前应用不匹配",
        "about_update_verify_signature" to "安装包签名与当前应用不一致，请改用浏览器下载",
        "about_version" to "版本",
        "agent_compaction_failed" to "压缩失败：%1${'s}s",
        "agent_complete_notification_background_body" to "后台任务「%1${'s」已完成}s」已完成",
        "agent_complete_notification_background_multi_body" to "%1${'d 个后台任务已完成}d 个后台任务已完成",
        "agent_complete_notification_body" to "点击返回查看",
        "agent_complete_notification_title" to "任务完成",
        "agent_context_no_compaction" to "当前上下文无需压缩（消息过少）",
        "agent_input_budget_exceeded" to "上下文超过当前模型的可用输入预算，请缩短输入或减少工具定义。",
        "agent_model_not_selected" to "未选择",
        "agent_stop_context_exceeded" to "本次输入超出模型上下文窗口，请先压缩上下文或新建会话。",
        "agent_stop_refusal" to "模型基于内容安全策略拒绝了本次回答。",
        "agent_stopped_by_user" to "已被用户停止",
        "agent_tool_executing" to "%1${'s 执行中…}s 执行中…",
        "agreement_accept" to "同意并继续",
        "agreement_decline" to "不同意",
        "agreement_notice_conjunction" to "和",
        "agreement_notice_prefix" to "使用前，请阅读并同意",
        "agreement_notice_suffix" to "，了解使用规则与数据处理方式。\n点击“同意并继续”表示你已阅读并同意上述两份文件。",
        "agreement_privacy" to "隐私政策",
        "agreement_privacy_link" to "《隐私政策》",
        "agreement_privacy_url" to "https://aicode.murk.top/privacy",
        "agreement_terms" to "用户协议",
        "agreement_terms_link" to "《用户协议》",
        "agreement_terms_url" to "https://aicode.murk.top/terms",
        "agreement_title" to "欢迎使用 AiCode",
        "app_name" to "AiCode",
        "ask_confirm" to "确认",
        "ask_confirm_title" to "AI 需要你的确认",
        "ask_custom_answer" to "输入自定义答案",
        "ask_input_hint" to "请输入…",
        "ask_supplement" to "补充",
        "backup_actions" to "操作",
        "backup_data_app_settings" to "应用设置",
        "backup_data_chat_history" to "聊天记录",
        "backup_data_mcp" to "MCP 服务器",
        "backup_data_permissions" to "授权规则",
        "backup_data_remote" to "连接与同步",
        "backup_data_workspace" to "工作区文件",
        "backup_export_btn" to "导出",
        "backup_export_failed" to "导出失败",
        "backup_export_subtitle" to "选择要导出的数据，可设置口令加密",
        "backup_export_title" to "导出备份",
        "backup_exported" to "备份已导出",
        "backup_import_btn" to "导入",
        "backup_import_done" to "导入完成",
        "backup_import_failed" to "导入失败",
        "backup_import_subtitle" to "从备份文件还原数据",
        "backup_import_title" to "导入备份",
        "backup_net_access_key" to "Access Key",
        "backup_net_add_target" to "添加备份目标",
        "backup_net_base_path" to "远端目录",
        "backup_net_base_path_hint" to "WebDAV 目录或 S3 对象前缀",
        "backup_net_bucket" to "存储桶",
        "backup_net_delete_failed" to "删除云端备份失败",
        "backup_net_delete_target_confirm" to "删除备份目标 %1${'s？}s？",
        "backup_net_edit_target" to "编辑备份目标",
        "backup_net_endpoint" to "服务器地址",
        "backup_net_endpoint_hint" to "如 https://dav.example.com/dav",
        "backup_net_list_failed" to "读取云端备份失败",
        "backup_net_loading" to "正在读取云端备份…",
        "backup_net_name" to "名称",
        "backup_net_no_target" to "尚未添加网络备份目标",
        "backup_net_no_target_hint" to "点击右上角 + 添加 WebDAV 或 S3 备份目标",
        "backup_net_password" to "密码",
        "backup_net_path_style" to "路径风格寻址",
        "backup_net_region" to "区域",
        "backup_net_remote_delete" to "删除",
        "backup_net_remote_delete_confirm" to "删除云端备份 %1${'s？该操作不可撤销。}s？该操作不可撤销。",
        "backup_net_remote_empty" to "云端暂无备份",
        "backup_net_remote_list_title" to "云端备份",
        "backup_net_remote_restore" to "恢复",
        "backup_net_restore" to "从云端恢复",
        "backup_net_restore_subtitle" to "从所选目标下载备份并还原",
        "backup_net_secret_key" to "Secret Key",
        "backup_net_section" to "网络备份",
        "backup_net_session_token" to "Session Token（可选）",
        "backup_net_targets" to "备份目标",
        "backup_net_test_connection" to "测试连接",
        "backup_net_test_failed" to "连接失败",
        "backup_net_test_success" to "连接成功",
        "backup_net_testing" to "正在测试连接…",
        "backup_net_type_s3" to "对象存储 (S3)",
        "backup_net_type_webdav" to "WebDAV",
        "backup_net_upload" to "上传到云端",
        "backup_net_upload_done" to "已上传到 %1${'s}s",
        "backup_net_upload_failed" to "上传失败",
        "backup_net_upload_password_hint" to "备份将上传到云端，为避免 API Key、SSH 凭据等明文泄露，必须设置口令加密（AES-GCM）",
        "backup_net_upload_password_required" to "云端备份必须设置加密口令",
        "backup_net_upload_short" to "上传",
        "backup_net_upload_subtitle" to "导出当前备份并上传到所选目标",
        "backup_net_uploading" to "正在上传备份…",
        "backup_net_username" to "用户名",
        "backup_operation_failed" to "操作失败",
        "backup_password_hint" to "留空则不加密，输出明文 tar.gz；填写则用口令加密（AES-GCM）",
        "backup_password_input" to "输入备份口令",
        "backup_password_label" to "口令（可选）",
        "backup_password_optional_hint" to "若备份未加密可留空",
        "backup_processing" to "处理中",
        "backup_processing_data" to "正在处理数据…",
        "backup_read_failed" to "读取备份文件失败",
        "backup_restore_workspaces_confirm" to "确认恢复",
        "backup_restore_workspaces_files" to "%1${'d 个文件}d 个文件",
        "backup_restore_workspaces_select_all" to "全选",
        "backup_restore_workspaces_title" to "恢复工作区",
        "backup_restore_workspaces_warning" to "将直接覆盖本地同名工作区中的文件，且无法撤销，请谨慎勾选",
        "backup_restored_data" to "已还原数据：",
        "backup_select_data" to "选择导出数据",
        "backup_set_password" to "设置导出口令",
        "backup_settings_covered" to "主题、保活、日志、识图、同步偏好已覆盖",
        "backup_stat_chat_messages" to "· 聊天消息 %1${'s 条}s 条",
        "backup_stat_chat_sessions" to "· 聊天会话 %1${'s 个}s 个",
        "backup_stat_mcp_servers" to "· MCP 服务器 %1${'s 个}s 个",
        "backup_stat_permission_rules" to "· 全局授权规则 %1${'s 条}s 条",
        "backup_stat_providers" to "· AI 供应商 %1${'s 个}s 个",
        "backup_stat_remote_connections" to "· 远程连接 %1${'s 个}s 个",
        "backup_stat_remote_mounts" to "· 远程挂载 %1${'s 个}s 个",
        "backup_stat_todo_items" to "· 待办项 %1${'s 条}s 条",
        "backup_stat_workspace_files" to "· 工作区文件 %1${'s 个}s 个",
        "backup_tab_local" to "本地备份",
        "backup_workspace_git_hint" to "工作区文件建议通过 Git 提交备份，导出备份仅作备用",
        "backup_write_failed" to "写入失败：%1${'s}s",
        "backup_wrong_password" to "备份口令错误，或加密备份文件已损坏",
        "browser_address_hint" to "输入网址或搜索",
        "browser_back" to "后退",
        "browser_close_tab" to "关闭标签页",
        "browser_devtools" to "开发者工具",
        "browser_devtools_desc" to "开启移动端 Eruda 调试控制台",
        "browser_devtools_title" to "开发者工具",
        "browser_dialog_alert_title" to "网页提示",
        "browser_dialog_confirm_title" to "网页确认",
        "browser_dialog_prompt_title" to "网页输入",
        "browser_empty_click_hint" to "点击上方输入网址开始浏览",
        "browser_empty_hint" to "在上方地址栏输入网址开始浏览",
        "browser_forward" to "前进",
        "browser_menu" to "更多设置",
        "browser_menu_general_section" to "页面与工具",
        "browser_menu_title" to "浏览器设置与工具",
        "browser_new_tab" to "新建标签页",
        "browser_night_mode_desc" to "降低页面亮度并启用深色偏好",
        "browser_night_mode_off" to "开启夜间模式",
        "browser_night_mode_on" to "关闭夜间模式",
        "browser_night_mode_title" to "夜间模式",
        "browser_not_ready" to "浏览器未就绪，请在地址栏输入网址开始浏览",
        "browser_reload" to "刷新",
        "browser_tab_default_title" to "新标签页",
        "browser_tabs_count" to "标签页 (%1${'d)}d)",
        "browser_tabs_manage" to "标签页管理",
        "browser_title" to "浏览器",
        "browser_ua_android_phone" to "安卓手机",
        "browser_ua_android_phone_desc" to "Chrome Mobile (Android)",
        "browser_ua_android_tablet" to "安卓平板",
        "browser_ua_android_tablet_desc" to "Chrome Tablet (Android)",
        "browser_ua_applied_toast" to "已切换浏览器标识并刷新页面",
        "browser_ua_custom" to "自定义",
        "browser_ua_custom_desc" to "手动指定 User-Agent 字符串",
        "browser_ua_custom_dialog_title" to "自定义 User-Agent",
        "browser_ua_custom_input_hint" to "输入完整的 User-Agent 字符串",
        "browser_ua_default" to "系统默认",
        "browser_ua_default_desc" to "当前设备原生 WebView 标识",
        "browser_ua_desc" to "模拟不同设备与环境访问网页",
        "browser_ua_desktop_pc" to "桌面电脑",
        "browser_ua_desktop_pc_desc" to "Chrome Desktop (Windows PC)",
        "browser_ua_ipad" to "苹果 iPad",
        "browser_ua_ipad_desc" to "Tablet Safari (iPadOS)",
        "browser_ua_iphone" to "苹果 iPhone",
        "browser_ua_iphone_desc" to "Mobile Safari (iOS)",
        "browser_ua_section" to "浏览器标识 (User-Agent)",
        "chat_action_copy" to "复制文本",
        "chat_action_delete" to "删除消息",
        "chat_action_edit" to "编辑消息",
        "chat_action_fork" to "从此处新建分支",
        "chat_add_attachment" to "添加附件",
        "chat_approve_and_implement" to "批准并实施",
        "chat_attachment_max" to "最多可同时发送 %1${'s 个附件}s 个附件",
        "chat_attachment_open" to "打开 %1${'s}s",
        "chat_attachment_prefix" to "附件：",
        "chat_attachment_preview" to "预览 %1${'s}s",
        "chat_attachment_uploaded" to "已上传 %1${'s 个附件}s 个附件",
        "chat_attachment_uploaded_partial" to "已上传 %1${'s 个附件，部分附件未加入}s 个附件，部分附件未加入",
        "chat_attachment_uploading" to "正在上传 %1${'d 个文件…}d 个文件…",
        "chat_batch_delete_confirm" to "确定删除选中的 %1${'d 个会话？包含的所有消息都将被清除，且无法恢复。}d 个会话？包含的所有消息都将被清除，且无法恢复。",
        "chat_batch_delete_title" to "删除所选会话",
        "chat_batch_select" to "批量选择",
        "chat_bg_command_done" to "后台命令已完成",
        "chat_bg_commands_done" to "%1${'d 个后台任务已完成}d 个后台任务已完成",
        "chat_bg_commands_partial_failed" to "%1${'d 个后台任务结束，%2$d 个失败}d 个后台任务结束，%2${'d 个失败}d 个失败",
        "chat_cache_hit_rate" to "缓存命中率 %1${'s}s",
        "chat_camera_file_failed" to "无法创建拍照临时文件",
        "chat_compaction_failed" to "上下文压缩失败",
        "chat_compressing_context" to "正在压缩上下文",
        "chat_connecting_remote" to "正在连接远程服务器…",
        "chat_context_compressed" to "上下文已压缩",
        "chat_context_usage" to "上下文已用 %1${'d%%}d%%",
        "chat_context_usage_estimated" to "预计占可用输入预算 %1${'d%%}d%%",
        "chat_continue_feedback" to "继续反馈",
        "chat_copied" to "已复制",
        "chat_copy" to "复制",
        "chat_delete_session" to "删除会话",
        "chat_delete_session_confirm" to "确定删除「%1${'s」？该会话的所有消息都将被清除，且无法恢复。}s」？该会话的所有消息都将被清除，且无法恢复。",
        "chat_edit_dialog_hint" to "请输入消息内容",
        "chat_edit_dialog_title" to "编辑消息",
        "chat_error_title" to "请求失败",
        "chat_export_session" to "导出会话",
        "chat_export_session_done" to "会话已导出",
        "chat_export_session_failed" to "导出失败",
        "chat_fork_title" to "Fork of %1${'s}s",
        "chat_goal_clear" to "清除目标",
        "chat_goal_label" to "目标·%1${'s}s",
        "chat_goal_pause" to "暂停目标",
        "chat_goal_resume" to "恢复目标",
        "chat_goal_status_active" to "进行中",
        "chat_goal_status_blocked" to "受阻",
        "chat_goal_status_completed" to "已完成",
        "chat_goal_status_exhausted" to "预算耗尽",
        "chat_goal_status_paused" to "已暂停",
        "chat_history" to "历史记录",
        "chat_image_too_large" to "图片超过 %1${'s，请压缩后重试}s，请压缩后重试",
        "chat_input_expand" to "展开输入框",
        "chat_input_fullscreen_title" to "全屏输入",
        "chat_input_hint" to "描述你的需求，我来帮你编写、修改或解释代码",
        "chat_input_placeholder" to "输入消息，与 AI 对话…",
        "chat_key_switched" to "已切换到第 %1${'d/%2$d 个 Key}d/%2${'d 个 Key}d 个 Key",
        "chat_mention_empty" to "没有匹配的文件",
        "chat_mention_error" to "加载文件列表失败",
        "chat_mention_loading" to "正在加载文件列表…",
        "chat_mention_unavailable" to "容器未就绪，无法搜索文件",
        "chat_more_options" to "更多选项",
        "chat_new_session" to "新建会话",
        "chat_new_session_btn" to "新会话",
        "chat_no_model_selected" to "未选择模型",
        "chat_no_models_hint" to "暂无可用模型，请在「AI 供应商」中添加",
        "chat_no_remote_connection" to "未连接远程服务器",
        "chat_no_sessions_hint" to "暂无会话",
        "chat_open_apk_permission" to "安装应用需要授权，请在跳转的设置中允许来自此来源的应用",
        "chat_open_browser" to "打开浏览器",
        "chat_open_file_missing" to "文件不存在或已被移动",
        "chat_open_file_no_app" to "没有可打开该文件的应用",
        "chat_open_file_unshareable" to "文件所在位置无法分享给其它应用",
        "chat_open_git" to "打开 Git",
        "chat_open_sidebar" to "打开侧边栏",
        "chat_open_terminal" to "打开终端",
        "chat_perm_always_allow" to "始终允许",
        "chat_perm_always_prefix" to "「始终允许」将在本项目记住：",
        "chat_perm_always_tool_desc" to "「始终允许」将在本项目记住：该工具的全部调用",
        "chat_perm_deny" to "拒绝",
        "chat_perm_session_label" to "会话「%1${'s」}s」",
        "chat_perm_single_use_desc" to "含命令替换/管道/重定向，无法记住，仅可单次放行",
        "chat_pin_session" to "置顶",
        "chat_placeholder" to "有什么可以帮你的？",
        "chat_plan_completed" to "计划已完成",
        "chat_queue_drag" to "拖拽排序",
        "chat_queue_edit" to "编辑排队消息",
        "chat_queue_hint" to "AI 正在处理中，回车发送将自动排队",
        "chat_queue_interject" to "立即插入给 AI",
        "chat_queue_remove" to "移除排队消息",
        "chat_queue_title" to "待发送队列（%1${'d）}d）",
        "chat_read_file_failed" to "无法读取所选文件",
        "chat_reasoning_cannot_disable" to "该模型无法关闭思考，已改用最低档",
        "chat_reasoning_default_tag" to "默认",
        "chat_reasoning_effective" to "实际生效：%1${'s}s",
        "chat_reasoning_effective_tag" to "生效",
        "chat_reasoning_effort" to "思考强度",
        "chat_reasoning_effort_high" to "高",
        "chat_reasoning_effort_low" to "低",
        "chat_reasoning_effort_max" to "最高",
        "chat_reasoning_effort_medium" to "中",
        "chat_reasoning_effort_minimal" to "极低",
        "chat_reasoning_effort_none" to "不思考",
        "chat_reasoning_effort_xhigh" to "极高",
        "chat_reasoning_endpoint_weak" to "当前端点可能忽略「关闭」，改用该厂商的新版端点（Responses）才能真关闭",
        "chat_reasoning_off_short" to "不思考",
        "chat_reasoning_toggle" to "思考",
        "chat_reasoning_unknown_hint" to "未识别到该模型支持的档位，所选档位可能不生效",
        "chat_remote_connect_failed" to "远程服务器连接失败，请检查设置",
        "chat_remove_attachment" to "移除附件",
        "chat_rename_session" to "重命名会话",
        "chat_retrying" to "正在重试 %1${'s/%2$s}s/%2${'s}s",
        "chat_search_clear" to "清除",
        "chat_search_hint" to "搜索聊天记录",
        "chat_search_no_result" to "未找到匹配的聊天记录",
        "chat_select_workspace_first" to "请先选择工作区",
        "chat_send" to "发送",
        "chat_session_name" to "会话名",
        "chat_sessions_selected_count" to "已选 %1${'d 个会话}d 个会话",
        "chat_settings" to "设置",
        "chat_ssh_connected" to "SSH：已连接",
        "chat_ssh_connecting" to "SSH：连接中…",
        "chat_ssh_disconnected" to "SSH：未连接",
        "chat_ssh_failed" to "SSH：连接失败",
        "chat_status_asking_user" to "正在询问用户",
        "chat_status_browsing_web" to "正在操作浏览器",
        "chat_status_calling_tool" to "正在调用工具",
        "chat_status_editing_file" to "正在编辑文件",
        "chat_status_generating" to "正在生成",
        "chat_status_generating_image" to "正在生成图片",
        "chat_status_reading_file" to "正在读取文件",
        "chat_status_reading_memory" to "正在读取记忆",
        "chat_status_running_command" to "正在执行命令",
        "chat_status_searching_web" to "正在联网搜索",
        "chat_status_starting_subagent" to "正在启动子代理",
        "chat_status_thinking" to "正在思考",
        "chat_status_updating_todo" to "正在整理待办",
        "chat_status_viewing_image" to "正在查看图片",
        "chat_stop" to "停止",
        "chat_take_photo" to "拍照",
        "chat_task_duration" to "本轮总耗时 %1${'s}s",
        "chat_thinking_done" to "思考完成",
        "chat_thinking_done_time" to "思考完成（耗时 %1${'s 秒）}s 秒）",
        "chat_thinking_process" to "思考过程",
        "chat_thinking_running_time" to "正在思考（耗时 %1${'s 秒）}s 秒）",
        "chat_tool_calls_count" to "%1${'d 次工具调用}d 次工具调用",
        "chat_tool_failed" to "工具执行失败",
        "chat_tool_running" to "工具执行中",
        "chat_tool_succeeded" to "工具执行成功",
        "chat_turn_completed" to "已完成 %1${'s}s",
        "chat_turn_completed_plain" to "已完成",
        "chat_turn_running" to "执行中",
        "chat_unpin_session" to "取消置顶",
        "chat_unsupported_image_type" to "无法识别图片类型，仅支持 jpg/png/gif/webp",
        "chat_upload_failed" to "上传失败",
        "chat_upload_file" to "上传文件",
        "chat_upload_image" to "上传图片",
        "chat_uploaded_to" to "已上传到 %1${'s}s",
        "chat_workspace_unavailable" to "工作区不可用",
        "checkpoint_restore_code" to "仅恢复代码",
        "checkpoint_restore_code_and_conversation" to "恢复代码与对话",
        "checkpoint_restore_code_and_conversation_desc" to "将代码与对话均回滚至此检查点之前",
        "checkpoint_restore_code_desc" to "恢复代码至此点之前的快照，保持对话历史",
        "checkpoint_restore_conversation" to "仅恢复对话",
        "checkpoint_restore_conversation_desc" to "保持当前代码，将对话截断回滚至此点",
        "checkpoint_rewind_title" to "检查点与撤销",
        "checkpoint_skipped_multiple" to "已跳过 %1${'d 个文件：它们在快照之后被改动，未回滚以保留这些改动\n%2$s}d 个文件：它们在快照之后被改动，未回滚以保留这些改动\n%2${'s}s",
        "checkpoint_skipped_single" to "已跳过 1 个文件：它在快照之后被其他会话或你改过，未回滚以保留这些改动\n%1${'s}s",
        "checkpoint_target_prompt" to "目标消息：%1${'s}s",
        "common_add" to "添加",
        "common_ai_providers" to "AI 供应商",
        "common_allow" to "允许",
        "common_back" to "返回",
        "common_batch_delete" to "删除",
        "common_batch_deleting" to "正在删除…",
        "common_batch_disable" to "停用",
        "common_batch_enable" to "启用",
        "common_batch_reconnect" to "重连",
        "common_cancel" to "取消",
        "common_clear" to "清除",
        "common_close" to "关闭",
        "common_collapse" to "折叠",
        "common_collapse_action" to "收起",
        "common_confirm" to "确定",
        "common_copied_with_title" to "已复制：%1${'s}s",
        "common_copy" to "复制",
        "common_copy_success" to "已复制到剪贴板",
        "common_create" to "创建",
        "common_cut" to "剪切",
        "common_delete" to "删除",
        "common_deselect_all" to "取消全选",
        "common_disabled" to "已禁用",
        "common_done" to "完成",
        "common_edit" to "编辑",
        "common_enabled" to "已启用",
        "common_error" to "错误",
        "common_error_unknown" to "未知错误",
        "common_expand" to "展开",
        "common_file" to "文件",
        "common_go_to_settings" to "去设置",
        "common_got_it" to "知道了",
        "common_hide" to "隐藏",
        "common_image_load_failed" to "图片加载失败，文件可能已被移动或删除",
        "common_image_preview" to "图片预览",
        "common_label_with_api" to "%1${'s（%2$s）}s（%2${'s）}s）",
        "common_local" to "本地",
        "common_model" to "模型",
        "common_more" to "更多",
        "common_name" to "名称",
        "common_new_tab" to "新建标签",
        "common_next" to "下一页",
        "common_open_link_failed" to "无法打开链接",
        "common_other" to "其他",
        "common_overwrite" to "覆盖",
        "common_paste" to "粘贴",
        "common_prev" to "上一页",
        "common_rename" to "重命名",
        "common_reset" to "重置",
        "common_retry" to "重试",
        "common_save" to "保存",
        "common_scroll_to_bottom" to "回到底部",
        "common_select" to "选择",
        "common_select_all" to "全选",
        "common_select_mode" to "多选",
        "common_selected_count" to "已选 %1${'d 项}d 项",
        "common_show" to "显示",
        "common_switch" to "切换",
        "common_tool" to "工具",
        "common_tool_missing_argument" to "缺少参数：%1${'s。}s。",
        "common_username" to "用户名",
        "common_workspace" to "工作区",
        "compaction_no_models" to "暂无可用模型。请在「AI 供应商」中添加并配置模型后再选压缩专用模型。",
        "container_add_arg" to "添加参数",
        "container_add_binding" to "添加挂载",
        "container_add_env_var" to "添加环境变量",
        "container_add_image" to "添加镜像",
        "container_announcement_got_it" to "知道了",
        "container_announcement_title" to "常见问题",
        "container_arg_value" to "参数值",
        "container_builtin_badge" to "内置",
        "container_builtin_restorable" to "内置镜像删除后可在空列表恢复。",
        "container_channel_deleted" to "通道已删除",
        "container_custom_badge" to "自定义",
        "container_custom_image" to "自定义镜像",
        "container_default_badge" to "默认",
        "container_default_desc" to "远程工作区模式下，本地 MCP 服务运行在此容器上",
        "container_delete_config" to "删除镜像配置",
        "container_delete_confirm" to "确定删除「%1${'s」？%2$s}s」？%2${'s}s",
        "container_download_architecture" to "架构：%1${'s}s",
        "container_download_batch_delete_confirm" to "删除选中的 %1${'d 个镜像文件？已导入的容器将无法再解压。}d 个镜像文件？已导入的容器将无法再解压。",
        "container_download_batch_delete_title" to "删除已下载镜像",
        "container_download_cancel" to "取消下载",
        "container_download_checksum_failed" to "文件校验失败，可能被截断或篡改，请重试",
        "container_download_current_source" to "下载源：%1${'s}s",
        "container_download_current_source_title" to "选择下载源",
        "container_download_delete_confirm" to "删除「%1${'s」的下载文件？已导入的容器将无法再解压。}s」的下载文件？已导入的容器将无法再解压。",
        "container_download_delete_title" to "删除下载镜像",
        "container_download_done" to "已下载",
        "container_download_eol_ended" to "已停止支持",
        "container_download_eol_extended" to "已进入 %1${'s 延长支持}s 延长支持",
        "container_download_eol_extended_generic" to "已进入延长支持",
        "container_download_eol_fully_ends" to "完全停止支持：%1${'s}s",
        "container_download_eol_soon" to "即将停止支持",
        "container_download_eol_support_ends" to "常规支持结束：%1${'s}s",
        "container_download_failed" to "下载失败：%1${'s}s",
        "container_download_image" to "下载镜像",
        "container_download_import" to "导入",
        "container_download_imported_toast" to "已导入到容器列表，首次进入终端时自动解压初始化",
        "container_download_list_empty" to "暂无可用镜像",
        "container_download_newer_available" to "有新版可下载",
        "container_download_redownload" to "重新下载",
        "container_download_size" to "大小：%1${'s}s",
        "container_download_source_unavailable" to "当前源暂无此镜像",
        "container_download_start" to "下载",
        "container_edit_image" to "编辑镜像",
        "container_empty" to "暂无容器与镜像",
        "container_empty_desc" to "添加一个自定义镜像，或恢复内置 Alpine。",
        "container_env_name" to "变量名",
        "container_env_value" to "变量值",
        "container_env_vars" to "环境变量",
        "container_extra_bindings" to "挂载",
        "container_extra_proot_args" to "proot 参数",
        "container_file_selected" to "已选择文件",
        "container_importing" to "正在复制镜像文件…",
        "container_local_image" to "本地镜像",
        "container_mount_container" to "容器目录",
        "container_mount_local" to "本地目录",
        "container_no_args" to "无参数",
        "container_no_bindings" to "无挂载",
        "container_no_env_vars" to "无自定义环境变量",
        "container_no_sftp_channel" to "暂无可用的 SFTP 通道，请先在「连接与同步」页配置一个 SFTP 连接通道。",
        "container_not_ready_hint" to "容器尚未初始化，请先进入终端页面完成初始化后再执行命令",
        "container_remote_ssh" to "远程 SSH",
        "container_remote_workspace_path" to "远程工作区路径",
        "container_reset" to "重置",
        "container_reset_confirm" to "将删除「%1${'s」的 rootfs 目录（含已安装的工具与配置），下次使用时重新解压并初始化。是否继续？}s」的 rootfs 目录（含已安装的工具与配置），下次使用时重新解压并初始化。是否继续？",
        "container_reset_title" to "重置容器",
        "container_resetting_message" to "正在删除「%1${'s」的数据…\n已清理 %2$s 项}s」的数据…\n已清理 %2${'s 项}s 项",
        "container_resetting_title" to "正在清理容器数据",
        "container_restore_builtin" to "恢复内置 Alpine",
        "container_rootfs_will_be_cleared" to "其 rootfs 目录会被一并清除。",
        "container_select_image_file" to "选择 tar.gz / tgz / tar.xz / txz 镜像文件",
        "container_select_ssh_channel" to "选择 SSH 通道",
        "container_set_as_default" to "设为默认容器",
        "container_shell_desc" to "shell: %1${'s}s",
        "container_shell_path" to "Shell",
        "container_sort_long_press" to "长按拖动排序",
        "container_ssh_badge" to "SSH",
        "container_ssh_channel" to "SSH 连接通道",
        "container_switch_confirm" to "切换到「%1${'s」后，当前正在运行的 AI 会话将被停止、终端标签将被关闭。是否继续切换？}s」后，当前正在运行的 AI 会话将被停止、终端标签将被关闭。是否继续切换？",
        "container_switch_image" to "切换容器与镜像",
        "crash_banner_title" to "应用运行异常",
        "crash_copied" to "错误信息已复制",
        "crash_copy" to "复制错误信息",
        "crash_exit" to "退出应用",
        "crash_field_device" to "设备型号",
        "crash_field_memory" to "内存用量",
        "crash_field_screen" to "发生页面",
        "crash_field_thread" to "异常线程",
        "crash_field_time" to "发生时间",
        "crash_field_version" to "应用版本",
        "crash_field_workspace_mode" to "工作区模式",
        "crash_log_hint" to "完整日志文件位置：%1${'s}s",
        "crash_message" to "应用出现异常，错误信息已自动记录。请复制下方内容并反馈给开发者，帮助我们改进应用。",
        "crash_mode_local" to "本地 PRoot 容器",
        "crash_mode_remote" to "远程 SSH",
        "crash_mode_unknown" to "未知",
        "crash_report_device" to "设备：%1${'s}s",
        "crash_report_log" to "日志文件：%1${'s}s",
        "crash_report_memory" to "内存：%1${'s}s",
        "crash_report_mode" to "工作区模式：%1${'s}s",
        "crash_report_screen" to "页面：%1${'s}s",
        "crash_report_thread" to "线程：%1${'s}s",
        "crash_report_time" to "时间：%1${'s}s",
        "crash_report_title" to "AiCode 崩溃报告",
        "crash_report_version" to "版本：%1${'s}s",
        "crash_restart" to "重启应用",
        "crash_screen_chat" to "聊天",
        "crash_screen_git" to "Git",
        "crash_screen_settings" to "设置",
        "crash_screen_terminal" to "终端",
        "crash_screen_unknown" to "未知",
        "crash_section_environment" to "环境与运行状态",
        "crash_section_stack" to "异常调用栈",
        "crash_title" to "应用遇到未知错误",
        "credential_add" to "添加凭据",
        "credential_add_github_desc" to "通过 GitHub 账号一键授权，无需手动创建 Token",
        "credential_add_github_title" to "GitHub 快捷登录",
        "credential_add_manual" to "手动添加",
        "credential_add_manual_desc" to "填写远程主机、用户名与访问令牌",
        "credential_add_manual_subtitle" to "适用于 GitLab、Gitee、自建服务器或个人访问令牌",
        "credential_add_manual_title" to "手动配置凭据",
        "credential_add_recommend" to "推荐",
        "credential_add_sheet_subtitle" to "选择快捷授权登录或手动配置访问凭据",
        "credential_add_sheet_title" to "添加凭据",
        "credential_auto_inject_enable" to "开启",
        "credential_auto_inject_hint" to "把应用中保存的 git 凭据同步到远程服务器，仅对当前工作区根目录下的仓库生效，不影响你在服务器上手动使用 git",
        "credential_auto_inject_prompt_body" to "是否让 AiCode 把应用中保存的 git 凭据同步到该远程服务器？仅对当前工作区根目录下的仓库生效，不会改动服务器的全局 git 配置。",
        "credential_auto_inject_prompt_title" to "自动注入 git 凭证？",
        "credential_auto_inject_title" to "自动注入凭证",
        "credential_device_authorize_and_open" to "复制并前往授权",
        "credential_device_bad_client" to "客户端 ID 无效，请检查应用配置",
        "credential_device_card_label" to "设备授权码",
        "credential_device_code_copied" to "已复制设备码",
        "credential_device_code_copied_opening" to "已复制设备码，正在打开浏览器…",
        "credential_device_copy_code" to "仅复制设备码",
        "credential_device_denied" to "授权已被拒绝",
        "credential_device_expired" to "设备码已过期，请重试",
        "credential_device_failed" to "登录失败：%1${'s}s",
        "credential_device_failed_title" to "授权未能完成",
        "credential_device_flow_disabled" to "该应用未启用设备码流，请在 GitHub App 设置中开启",
        "credential_device_install_later" to "稍后",
        "credential_device_install_now" to "去安装",
        "credential_device_install_required" to "已登录为 %1${'s。还需把 AiCode 安装到你的仓库，之后才能推送/拉取代码。}s。还需把 AiCode 安装到你的仓库，之后才能推送/拉取代码。",
        "credential_device_instruction" to "在浏览器打开下方地址，输入设备码完成授权",
        "credential_device_open_browser" to "打开浏览器",
        "credential_device_requesting" to "正在请求设备码…",
        "credential_device_step_1" to "1. 复制上方 8 位设备验证码",
        "credential_device_step_2" to "2. 前往 GitHub 验证页面粘贴并确认授权",
        "credential_device_subtitle" to "通过 GitHub 官方设备流安全授权访问权限",
        "credential_device_success" to "已成功关联账号 %1${'s 并保存凭据}s 并保存凭据",
        "credential_device_success_title" to "授权成功",
        "credential_device_tap_to_copy" to "点击复制",
        "credential_device_title" to "GitHub 授权登录",
        "credential_device_unknown_error" to "未知错误",
        "credential_device_waiting" to "等待浏览器授权完成…",
        "credential_device_waiting_desc" to "完成授权后将自动绑定并保存凭据",
        "credential_edit" to "编辑凭据",
        "credential_empty_desc" to "点击右上角 + 添加：GitHub 快捷登录或手动配置访问令牌。配置后推送、拉取代码时会自动完成身份认证",
        "credential_empty_hint" to "尚未添加凭据，点右上角 + 添加",
        "credential_empty_title" to "暂无 Git 凭据",
        "credential_host" to "远程主机",
        "credential_prompt_desc" to "填完即保存并自动重试。该凭据同时会保存到容器内 git，终端与 AI 执行裸 git 命令时也可直接使用。",
        "credential_prompt_title" to "需要 %1${'s 的登录凭据}s 的登录凭据",
        "credential_quick_login" to "快速登录",
        "credential_quick_login_desc" to "通过 GitHub 设备码授权登录",
        "credential_quick_login_github_desc" to "使用 GitHub 账号授权登录",
        "credential_quick_login_title" to "快速登录",
        "credential_remote_host" to "远程主机",
        "credential_save_and_retry" to "保存并重试",
        "credential_toast_deleted" to "凭据已删除",
        "credential_toast_identity_saved" to "署名与仓库地址已保存",
        "credential_toast_save_failed" to "保存失败：%1${'s}s",
        "credential_toast_saved" to "凭据已保存",
        "credential_token" to "访问令牌（Token / PAT）",
        "dashboard_exec_failed" to "执行失败",
        "dashboard_fetch_failed_retry" to "加载失败，点击重试",
        "dashboard_fetching" to "正在加载面板…",
        "dashboard_query_failed" to "查询失败",
        "dashboard_tap_to_query" to "点击刷新面板",
        "diff_binary_file" to "二进制文件，不支持文本差异对比",
        "diff_file_too_large" to "文件过大，已跳过差异计算",
        "diff_file_too_large_desc" to "超过 2000 行或含超长行的文件不做行级 diff，以避免渲染与内存压力。",
        "diff_no_changes" to "无差异",
        "drawer_collapse_subagents" to "收起子代理",
        "drawer_expand_subagents" to "展开子代理",
        "drawer_tab_files" to "文件",
        "editor_binary_unsupported" to "二进制文件，暂不支持打开",
        "editor_cursor_left" to "光标左移",
        "editor_cursor_right" to "光标右移",
        "editor_discard" to "放弃",
        "editor_dont_save" to "不保存",
        "editor_file_too_large" to "文件过大（%1${'d MB），暂不支持打开}d MB），暂不支持打开",
        "editor_font_size" to "字体大小",
        "editor_font_size_value" to "%1${'d sp}d sp",
        "editor_indent" to "缩进",
        "editor_load_failed" to "文件打开失败",
        "editor_md_preview" to "预览 Markdown",
        "editor_md_show_source" to "显示源码",
        "editor_redo" to "重做",
        "editor_save_and_exit" to "保存并退出",
        "editor_save_and_switch" to "保存并切换",
        "editor_save_conflict_cancel" to "取消",
        "editor_save_conflict_diff" to "- 磁盘当前内容\n+ 你的编辑\n%1${'s}s",
        "editor_save_conflict_discard" to "放弃我的修改",
        "editor_save_conflict_discarded" to "已放弃修改并重新载入磁盘内容",
        "editor_save_conflict_message" to "该文件在你打开之后已被 AI 或其他程序修改。直接保存会覆盖对方的改动。",
        "editor_save_conflict_overwrite" to "覆盖磁盘",
        "editor_save_conflict_title" to "文件已被修改",
        "editor_save_failed" to "保存失败",
        "editor_save_success" to "已保存",
        "editor_settings" to "编辑器设置",
        "editor_show_indent_guide" to "显示缩进参考线",
        "editor_show_whitespace" to "显示空白符号",
        "editor_show_wrap_arrow" to "显示自动换行箭头",
        "editor_undo" to "撤销",
        "editor_unsaved_message" to "当前文件有未保存的修改，是否保存？",
        "editor_unsaved_title" to "未保存的修改",
        "editor_word_wrap" to "自动换行",
        "file_browser_clipboard_copy" to "已复制",
        "file_browser_clipboard_cut" to "已剪切",
        "file_browser_collapse" to "折叠",
        "file_browser_create_failed" to "创建失败，可能已存在同名项或无写入权限",
        "file_browser_delete_failed" to "删除失败",
        "file_browser_delete_file_confirm" to "确定删除文件「%1${'s」？}s」？",
        "file_browser_delete_folder_confirm" to "确定删除文件夹「%1${'s」及其全部内容？}s」及其全部内容？",
        "file_browser_empty" to "空目录",
        "file_browser_error" to "读取目录失败",
        "file_browser_expand" to "展开",
        "file_browser_name_label" to "名称",
        "file_browser_new_file" to "新建文件",
        "file_browser_new_folder" to "新建文件夹",
        "file_browser_paste_conflict_message" to "目标位置已存在同名项目「%1${'s」，是否覆盖？}s」，是否覆盖？",
        "file_browser_paste_conflict_title" to "同名文件已存在",
        "file_browser_paste_here" to "粘贴到此处",
        "file_browser_refresh" to "刷新",
        "file_browser_rename_failed" to "重命名失败，可能已存在同名项",
        "file_browser_up" to "上一级",
        "ftp_address_copied" to "地址已复制",
        "ftp_allow_anonymous" to "允许匿名访问",
        "ftp_anonymous_confirm_message" to "开启后，同一局域网内任何设备无需凭据即可读写你的工作区，存在被他人访问的风险。",
        "ftp_anonymous_confirm_title" to "允许匿名访问？",
        "ftp_anonymous_desc" to "无需输入用户名和密码即可连接",
        "ftp_auto_start" to "应用启动时自启",
        "ftp_auto_start_desc" to "应用启动时后台自动运行 FTP 服务",
        "ftp_config_saved" to "配置已保存",
        "ftp_config_title" to "服务参数配置",
        "ftp_copy_address" to "复制地址",
        "ftp_error" to "错误：%1${'s}s",
        "ftp_listen_port" to "监听端口",
        "ftp_login_password" to "登录密码",
        "ftp_login_username" to "登录用户名",
        "ftp_not_running" to "服务未开启",
        "ftp_password_required" to "请先设置登录用户名与密码",
        "ftp_reserved_username" to "anonymous 是匿名登录的保留名称，请使用其它用户名",
        "ftp_running" to "运行中：%1${'s}s",
        "ftp_save_config" to "保存配置",
        "ftp_usage_item_1" to "手机与电脑连接同一 WiFi",
        "ftp_usage_item_2" to "电脑文件管理器输入下方地址访问",
        "ftp_usage_item_3" to "即可管理手机工作区里的项目",
        "ftp_usage_title" to "使用说明",
        "ftp_user_cache_cleanup_failed" to "无法清理旧 FTP 登录信息，服务未启动，请重试",
        "git_abort_merge_confirm" to "确定中止当前合并吗？未解决的冲突改动将被放弃，工作区将还原到合并前的状态。",
        "git_action_abort_merge" to "中止合并",
        "git_action_clone" to "克隆",
        "git_action_commit" to "提交",
        "git_action_copy_path" to "复制路径",
        "git_action_create_branch" to "创建分支",
        "git_action_create_tag" to "创建标签",
        "git_action_delete_branch" to "删除分支",
        "git_action_delete_dir" to "删除目录",
        "git_action_delete_file" to "删除文件",
        "git_action_delete_remote_branch" to "删除远程分支",
        "git_action_delete_tag" to "删除标签",
        "git_action_init" to "初始化",
        "git_action_merge" to "合并分支",
        "git_action_rename_branch" to "重命名分支",
        "git_action_restore_file" to "恢复文件",
        "git_action_revert" to "回退改动",
        "git_action_revert_all" to "全部回退",
        "git_action_stage_all" to "全部暂存",
        "git_action_stash" to "储藏改动",
        "git_action_unstage_all" to "全部取消暂存",
        "git_action_view_diff" to "查看差异",
        "git_add_to_gitignore" to "添加到 .gitignore",
        "git_ahead_count" to "待推送 %1${'s}s",
        "git_base_branch" to "基准分支",
        "git_behind_count" to "待拉取 %1${'s}s",
        "git_branch_name" to "分支名",
        "git_branch_synced" to "已同步",
        "git_checked_out" to "当前检出",
        "git_clean" to "工作区干净",
        "git_clone_desc" to "将远程仓库克隆到当前工作区根目录（工作区需为空）。若为私有仓库，可先在右上角凭据管理中配置访问凭据。",
        "git_clone_dialog_title" to "克隆 Git 仓库",
        "git_clone_repo" to "克隆远程仓库",
        "git_clone_url_hint" to "https://github.com/user/repo.git",
        "git_clone_url_label" to "仓库地址",
        "git_cloning_hint" to "正在克隆仓库，请稍候…",
        "git_commit_ai_generate" to "AI 生成",
        "git_commit_amend" to "追加到上次提交 (--amend)",
        "git_commit_changes" to "提交更改",
        "git_commit_count" to "提交记录 (%1${'s)}s)",
        "git_commit_dialog_title" to "提交更改",
        "git_commit_generating" to "正在生成…",
        "git_commit_message" to "提交信息",
        "git_commit_needs_identity" to "尚未配置提交署名，请在「凭据与署名」页填写用户名和邮箱",
        "git_computing_diff" to "正在计算差异…",
        "git_conflicted_count" to "存在冲突 (%1${'s)}s)",
        "git_copy_full_hash" to "复制完整哈希",
        "git_copy_short_hash" to "复制短哈希",
        "git_create_and_switch" to "创建并切换",
        "git_create_branch_from_commit" to "基于此提交创建分支",
        "git_create_tag_at_commit" to "在此提交创建标签",
        "git_create_tag_desc" to "从当前 HEAD（%1${'s）创建轻量标签}s）创建轻量标签",
        "git_create_tag_title" to "新建标签",
        "git_create_tracking_branch" to "从远程分支 %1${'s 创建本地跟踪分支并切换？}s 创建本地跟踪分支并切换？",
        "git_credentials_and_identity" to "凭据与署名",
        "git_credentials_count" to "凭据 (%1${'d)}d)",
        "git_current_branch" to "当前分支",
        "git_delete_branch" to "删除分支",
        "git_delete_local_branch_confirm" to "删除本地分支 %1${'s？}s？",
        "git_delete_remote_branch" to "删除远程分支",
        "git_delete_remote_branch_confirm" to "删除远程分支 %1${'s？该操作不可撤销。}s？该操作不可撤销。",
        "git_delete_tag" to "删除标签",
        "git_delete_tag_confirm" to "删除本地标签 %1${'s？}s？",
        "git_delete_untracked_confirm" to "删除 %1${'s？它从未被提交过，删除后无法通过 Git 恢复。}s？它从未被提交过，删除后无法通过 Git 恢复。",
        "git_detached_head_label" to "detached HEAD：当前不在任何分支上",
        "git_detached_head_warning" to "切换到标签 %1${'s 将进入 detached HEAD 状态——不处于任何分支上，新提交不属于任何分支。确定继续？}s 将进入 detached HEAD 状态——不处于任何分支上，新提交不属于任何分支。确定继续？",
        "git_expanded" to "已展开",
        "git_files_changed" to "%1${'s 个文件改动}s 个文件改动",
        "git_has_changes" to "工作区有改动",
        "git_identity_desc" to "用于每次提交的 author。容器全局生效，所有工作区共用。",
        "git_identity_title" to "提交署名",
        "git_ignore_dir" to "忽略整个目录 (%1${'s/)}s/)",
        "git_ignore_extension" to "忽略同类型文件 (*.%1${'s)}s)",
        "git_ignore_file" to "忽略此文件 (%1${'s)}s)",
        "git_init_desc_primary" to "可在此初始化新仓库，开始版本管理",
        "git_init_desc_secondary" to "若子目录已有 Git 仓库，也可直接选择使用",
        "git_init_repo" to "初始化新仓库",
        "git_latest_commit" to "最新提交",
        "git_latest_date" to "最新日期",
        "git_load_more_commits" to "上拉加载更早提交",
        "git_loading_branches" to "正在加载分支列表…",
        "git_loading_files" to "正在加载改动文件…",
        "git_merge_commit" to "合并提交",
        "git_merge_confirm" to "确定将分支 %1${'s 合并到当前分支（%2$s）吗？}s 合并到当前分支（%2${'s）吗？}s）吗？",
        "git_merge_into" to "合并到 %1${'s}s",
        "git_merge_no_ff" to "创建合并提交（--no-ff）",
        "git_merge_pending_hint" to "当前没有未解决的冲突。请确认并暂存需要保留的改动，然后提交以完成合并。",
        "git_merge_pending_title" to "合并待提交",
        "git_modified_count" to "已修改 (%1${'s)}s)",
        "git_modified_label" to "已修改",
        "git_new" to "新增",
        "git_new_branch" to "新建分支",
        "git_new_branch_name" to "新分支名",
        "git_no_branch" to "(无分支)",
        "git_no_branches" to "暂无分支",
        "git_no_checked_out_branch" to "未检出分支",
        "git_no_commits" to "暂无提交记录",
        "git_no_files_changed" to "该提交无文件改动",
        "git_no_identity" to "未配置署名，无法提交。请在「凭据」页填写 user.name / user.email。",
        "git_no_more_commits" to "没有更多了",
        "git_no_upstream" to "未设置上游",
        "git_not_a_repo" to "当前工作区还不是 Git 仓库",
        "git_open_in_editor" to "在编辑器中打开",
        "git_pick_existing_repo" to "选择已有仓库",
        "git_pick_repo_empty" to "当前工作区没有可选的目录",
        "git_pull" to "拉取",
        "git_pull_continue" to "继续拉取",
        "git_pull_dirty_confirm" to "当前工作区有未提交改动，拉取可能导致冲突。建议先提交或暂存。仍要继续拉取吗？",
        "git_push" to "推送",
        "git_push_keep_master" to "保留 master 推送",
        "git_push_master_confirm_desc" to "当前本地分支为 master，GitHub 与 GitLab 推荐使用 main 作为默认主分支。是否将本地分支重命名为 main 后再推送？",
        "git_push_master_confirm_title" to "分支命名建议",
        "git_push_rename_to_main" to "重命名为 main 并推送",
        "git_recent_commit" to "最近提交",
        "git_refresh" to "刷新",
        "git_remote" to "远程",
        "git_remote_url" to "remote.origin.url",
        "git_rename_branch" to "重命名分支",
        "git_rename_to" to "将 %1${'s 重命名为：}s 重命名为：",
        "git_repo_not_repo" to "非仓库",
        "git_repo_selector_title" to "选择仓库",
        "git_repo_url_title" to "仓库地址",
        "git_reset_confirm" to "确定将当前分支重置到提交 %1${'s 吗？}s 吗？",
        "git_reset_mixed" to "Mixed（保留工作区，清空暂存区）",
        "git_reset_soft" to "Soft（保留工作区与暂存区）",
        "git_reset_to_commit" to "重置到此提交 (Reset)",
        "git_restore_file_confirm" to "恢复已删除的 %1${'s？将从上次提交取回这个文件。}s？将从上次提交取回这个文件。",
        "git_revert_all_confirm" to "丢弃全部未暂存的改动（%1${'d 个文件），还原到上次暂存或提交的内容？已暂存的内容不受影响，但丢弃的改动无法找回。}d 个文件），还原到上次暂存或提交的内容？已暂存的内容不受影响，但丢弃的改动无法找回。",
        "git_revert_confirm_head" to "把 %1${'s 还原到上次提交的内容？该文件已暂存和未暂存的改动都会丢失，且无法找回。}s 还原到上次提交的内容？该文件已暂存和未暂存的改动都会丢失，且无法找回。",
        "git_revert_confirm_worktree" to "把 %1${'s 还原成上次暂存或提交的内容？未暂存的改动会丢失，且无法找回。}s 还原成上次暂存或提交的内容？未暂存的改动会丢失，且无法找回。",
        "git_search_commits_hint" to "搜索提交说明或作者…",
        "git_stage" to "暂存",
        "git_stage_all" to "暂存全部",
        "git_staged_count" to "已暂存 (%1${'s)}s)",
        "git_staged_label" to "已暂存",
        "git_stash_apply" to "应用 (Apply)",
        "git_stash_clear" to "清空全部",
        "git_stash_clear_confirm" to "确定清空所有储藏记录吗？该操作不可撤销。",
        "git_stash_count" to "储藏 (%1${'d)}d)",
        "git_stash_drop" to "丢弃 (Drop)",
        "git_stash_drop_confirm" to "确定丢弃储藏 %1${'s 吗？该操作不可撤销。}s 吗？该操作不可撤销。",
        "git_stash_empty" to "暂无储藏记录",
        "git_stash_include_untracked" to "包含未跟踪文件（-u）",
        "git_stash_message_hint" to "储藏说明（可选）",
        "git_stash_pop" to "弹出 (Pop)",
        "git_stash_title" to "储藏箱",
        "git_status_clean" to "工作区干净，无改动",
        "git_switch_branch" to "切换分支",
        "git_switch_confirm" to "确定切换到 %1${'s？}s？",
        "git_switch_repo" to "切换仓库",
        "git_tab_branches" to "分支",
        "git_tab_commits" to "提交",
        "git_tab_credentials" to "凭据",
        "git_tab_identity" to "署名",
        "git_tab_stash" to "储藏",
        "git_tab_status" to "状态",
        "git_tag_name" to "标签名",
        "git_tags" to "标签",
        "git_title" to "Git",
        "git_toast_action_failed" to "%1${'s失败：%2$s}s失败：%2${'s}s",
        "git_toast_action_refresh_failed" to "%1${'s（刷新失败）}s（刷新失败）",
        "git_toast_action_success" to "%1${'s成功}s成功",
        "git_toast_added_to_gitignore" to "已添加到 .gitignore",
        "git_toast_checkout_failed" to "切换失败：%1${'s}s",
        "git_toast_checkout_success" to "已切换到 %1${'s}s",
        "git_toast_diff_failed" to "加载差异失败",
        "git_toast_generate_commit_failed" to "AI 生成提交信息失败，请检查模型或网络",
        "git_toast_load_branches_failed" to "加载分支失败：%1${'s}s",
        "git_toast_load_more_failed" to "加载更多失败：%1${'s}s",
        "git_toast_no_diff_for_commit" to "暂存区与工作区均无差异，无法生成提交信息",
        "git_toast_no_remote_pull" to "未配置远程仓库，无法拉取",
        "git_toast_no_remote_push" to "未配置远程仓库，无法推送",
        "git_toast_not_repo" to "该目录不是 Git 仓库",
        "git_toast_path_copied" to "已复制路径",
        "git_toast_refresh_failed" to "刷新失败：%1${'s}s",
        "git_toast_switch_repo_failed" to "切换仓库失败：%1${'s}s",
        "git_tracking_branch" to "跟踪 %1${'s}s",
        "git_unstage" to "取消暂存",
        "git_unstage_all_confirm" to "将取消所有文件的暂存，但不会删除文件内容。确定继续吗？",
        "git_untracked_count" to "未跟踪 (%1${'s)}s)",
        "git_untracked_label" to "未跟踪",
        "image_gen_no_models" to "暂无可用模型。请在「AI 供应商」中添加并配置模型后（需支持图片输出，如 gpt-image-1 / dall-e-3），再选生图模型。",
        "language_follow_system" to "跟随系统",
        "log_clear_filter" to "清除筛选",
        "log_copied" to "已复制",
        "log_copy" to "复制",
        "log_copy_line" to "复制本条日志",
        "log_copy_menu_title" to "复制日志",
        "log_copy_message" to "复制消息内容",
        "log_copy_with_stack" to "复制本条及堆栈",
        "log_file_location" to "日志文件位于 Android/data//files/logs/。",
        "log_files" to "日志文件",
        "log_follow_latest" to "实时滚动",
        "log_level" to "日志等级",
        "log_level_desc" to "记录低于该等级的日志将被忽略。开发期建议 VERBOSE（全部记录）；NONE 关闭日志。",
        "log_mcp_prefix" to "MCP 日志：%1${'s}s",
        "log_no_match" to "当前筛选条件下没有日志",
        "log_search_hint" to "搜索日志",
        "log_search_results" to "匹配 %1${'s 行}s 行",
        "log_show_last_lines" to "显示最后 %1${'s/%2$s 行}s/%2${'s 行}s 行",
        "log_show_lines" to "显示 %1${'s 行}s 行",
        "mcp_add" to "添加 MCP",
        "mcp_add_arg" to "添加参数",
        "mcp_add_env_var" to "添加环境变量",
        "mcp_add_from_json" to "从 JSON 导入",
        "mcp_add_from_json_desc" to "粘贴标准 mcpServers 配置批量导入",
        "mcp_add_header" to "添加请求头",
        "mcp_add_manual" to "手动填写",
        "mcp_add_manual_desc" to "逐个填写地址、请求头或启动命令",
        "mcp_add_sheet_title" to "添加 MCP 服务器",
        "mcp_arg_hint" to "如 -y 或 @modelcontextprotocol/server-xxx",
        "mcp_arg_value" to "参数值",
        "mcp_args" to "命令参数",
        "mcp_auth_advanced" to "高级认证选项",
        "mcp_auth_cancel" to "取消授权",
        "mcp_auth_client_id" to "client_id（可选）",
        "mcp_auth_client_id_hint" to "预注册的客户端 ID；留空则尝试动态注册",
        "mcp_auth_desc" to "用于需要浏览器登录授权的远程 MCP 服务",
        "mcp_auth_enable" to "OAuth 认证",
        "mcp_auth_failed" to "授权失败：%1${'s}s",
        "mcp_auth_in_progress" to "等待浏览器授权…",
        "mcp_auth_in_progress_hint" to "已在系统浏览器打开登录页，授权完成后将自动返回并连接",
        "mcp_auth_login" to "登录 / 重新授权",
        "mcp_auth_logout" to "退出登录",
        "mcp_auth_reauthorize" to "重新授权",
        "mcp_auth_save_first" to "保存服务器配置后，即可在此发起登录授权",
        "mcp_auth_server" to "授权服务器（可选）",
        "mcp_auth_server_hint" to "覆盖自动发现的授权服务器地址",
        "mcp_auth_status_authorized" to "已登录",
        "mcp_auth_status_authorizing" to "授权中",
        "mcp_auth_status_label" to "授权状态",
        "mcp_auth_status_unauthorized" to "未登录",
        "mcp_auth_success" to "授权成功，已连接",
        "mcp_basic_settings" to "基础设置",
        "mcp_batch_delete_confirm" to "确定删除选中的 %1${'d 个 MCP 服务器吗？}d 个 MCP 服务器吗？",
        "mcp_batch_delete_title" to "删除 MCP 服务器",
        "mcp_command" to "启动命令",
        "mcp_command_hint" to "例如：npx",
        "mcp_connected" to "已连接",
        "mcp_connecting" to "连接中…",
        "mcp_connection_failed" to "连接失败",
        "mcp_custom_headers" to "自定义请求头",
        "mcp_details" to "详情",
        "mcp_disabled" to "已禁用",
        "mcp_edit" to "编辑 MCP",
        "mcp_empty" to "还没有配置 MCP 服务器",
        "mcp_empty_hint" to "点击右上角 + 可以添加远程 HTTP 或本地 stdio 服务器",
        "mcp_enabled" to "是否启用",
        "mcp_env_name" to "变量名称",
        "mcp_env_name_hint" to "如 API_KEY",
        "mcp_env_value" to "变量值",
        "mcp_env_value_hint" to "如 sk-xxxx",
        "mcp_env_vars" to "环境变量",
        "mcp_header_name" to "请求头名称",
        "mcp_header_name_hint" to "如 Authorization",
        "mcp_header_value" to "请求头值",
        "mcp_header_value_hint" to "如 Bearer xxxxxx",
        "mcp_json_import_action" to "导入",
        "mcp_json_import_empty" to "未解析到任何 MCP 服务器",
        "mcp_json_import_hint" to "粘贴 mcpServers 配置，例如 {\"mcpServers\":{\"demo\":{\"url\":\"https://...\"}}}",
        "mcp_json_import_title" to "从 JSON 导入",
        "mcp_local_stdio" to "本地 stdio",
        "mcp_name_hint" to "例如：fetch、github、gitlab",
        "mcp_name_invalid" to "名称只能包含字母、数字、下划线和连字符",
        "mcp_needs_auth" to "需要登录",
        "mcp_no_args" to "无额外命令行参数",
        "mcp_no_custom_headers" to "暂无配置的自定义 Header",
        "mcp_no_description" to "无描述",
        "mcp_no_env_vars" to "无自定义环境变量",
        "mcp_no_tools_hint" to "未检测到工具，点击右上角刷新按钮重试",
        "mcp_not_connected" to "未连接",
        "mcp_refresh_tools" to "刷新工具",
        "mcp_remote_http" to "远程 HTTP",
        "mcp_save_first_hint" to "请先保存并连接服务器后查看工具列表",
        "mcp_scope_global" to "全局",
        "mcp_scope_label" to "作用域",
        "mcp_scope_project" to "项目",
        "mcp_search_empty" to "未找到匹配的 MCP 服务器",
        "mcp_search_placeholder" to "搜索 MCP 服务器…",
        "mcp_server_url" to "服务器地址",
        "mcp_server_url_hint" to "例如：https://api.example.com/sse",
        "mcp_tools_count" to "%1${'d 个工具}d 个工具",
        "mcp_tools_disable_all" to "全部禁用",
        "mcp_tools_enable_all" to "全部启用",
        "mcp_tools_search" to "搜索工具名称或描述…",
        "mcp_tools_search_empty" to "未找到匹配的工具",
        "mcp_tools_summary" to "已启用 %1${'d / %2$d 个工具}d / %2${'d 个工具}d 个工具",
        "mcp_transport_type" to "传输类型",
        "mcp_type_stdio" to "stdio",
        "mcp_view_logs" to "查看此 MCP 日志",
        "network_error_connect" to "连接被拒绝：请检查 Base URL 与端口",
        "network_error_dns" to "DNS 解析失败：域名没有解析到可用地址，请检查网络、Wi-Fi 的 DNS 或代理设置",
        "network_error_ssl" to "SSL 握手失败：请检查证书或代理设置",
        "network_error_timeout" to "请求超时：网络较慢或服务未响应",
        "notification_agent_live_title_multi" to "%1${'d 个任务进行中}d 个任务进行中",
        "notification_approval_action_allow" to "允许",
        "notification_approval_action_approve_plan" to "批准",
        "notification_approval_action_reject" to "拒绝",
        "notification_approval_label_permission" to "需要授权",
        "notification_approval_label_plan" to "等待计划审批",
        "notification_approval_label_question" to "需要回答",
        "notification_approval_multi_title" to "%1${'d 个任务等待确认}d 个任务等待确认",
        "notification_approval_needed" to "需要审批",
        "notification_approval_title" to "AI 等待你的确认",
        "notification_channel_agent_approval" to "授权审批通知",
        "notification_channel_agent_approval_desc" to "AI 在后台请求授权、提问或等待计划审批时弹出的通知",
        "notification_channel_agent_complete" to "Agent 完成通知",
        "notification_channel_agent_complete_desc" to "Agent 在后台完成任务时弹出的通知",
        "notification_channel_agent_live" to "AI 实时进度",
        "notification_channel_agent_live_desc" to "AI 在后台工作时展示当前进度的通知",
        "notification_channel_terminal_service" to "终端服务",
        "notification_channel_terminal_service_desc" to "后台终端任务通知",
        "notification_keepalive_enabled" to "后台保活已开启",
        "notification_keepalive_persistent_sessions" to "后台保活 · 终端任务: %1${'d}d",
        "notification_keepalive_running_sessions" to "运行中的终端任务: %1${'d}d",
        "onboarding_choose_model_in_sheet_desc" to "点击刚刚添加的模型，指定当前对话使用的 AI。",
        "onboarding_choose_model_in_sheet_title" to "选择会话模型",
        "onboarding_config_provider_desc" to "点击进入 AI 供应商管理列表，准备配置模型供应商。",
        "onboarding_config_provider_title" to "进入 AI 供应商",
        "onboarding_done" to "完成",
        "onboarding_enter_settings_desc" to "点击进入 App 设置，管理你的 AI 供应商与模型配置。",
        "onboarding_enter_settings_title" to "进入设置",
        "onboarding_next" to "下一步",
        "onboarding_open_model_picker_desc" to "回到主页，点击输入框左侧的模型图标打开选择面板。",
        "onboarding_open_model_picker_title" to "打开模型面板",
        "onboarding_open_sidebar_desc" to "点击左上角按钮打开侧边栏，前往设置配置你的 AI 模型。",
        "onboarding_open_sidebar_title" to "打开侧边栏",
        "onboarding_provider_add_desc" to "点击「+」添加供应商，支持 OpenAI、Claude、Gemini、DeepSeek 等官方供应商或自定义中转。",
        "onboarding_provider_add_title" to "添加供应商",
        "onboarding_provider_config_info_desc" to "输入你的 API 密钥。官方预设已内置根地址，第三方中转可修改 Base URL。",
        "onboarding_provider_config_info_title" to "填写配置信息",
        "onboarding_provider_fetch_models_desc" to "配置好密钥后，点击「拉取模型」即可自动同步可用模型列表。",
        "onboarding_provider_fetch_models_title" to "拉取模型",
        "onboarding_provider_select_fetched_model_desc" to "已获取到可用模型，点击添加按钮将该模型加入当前供应商。",
        "onboarding_provider_select_fetched_model_title" to "添加拉取的模型",
        "onboarding_send_message_desc" to "模型已就绪，在输入框输入你的编程需求或问题，开始与 AI 助手协同工作。",
        "onboarding_send_message_title" to "开始对话",
        "onboarding_skip" to "跳过",
        "onboarding_step_badge" to "新手引导 %1${'d/%2$d}d/%2${'d}d",
        "perm_current_project" to "当前项目：%1${'s}s",
        "perm_current_project_none" to "当前项目（未选择）",
        "perm_deny" to "禁止",
        "perm_disable_safety_interception" to "禁用安全拦截",
        "perm_disable_safety_interception_desc" to "开启后 AUTO 模式下不再拦截灾难性删除，Shizuku 也不再逐次确认",
        "perm_entire_tool" to "整个工具",
        "perm_global" to "全局",
        "perm_no_global_rules" to "暂无全局规则",
        "perm_no_project_rules" to "本项目暂无规则",
        "perm_promote_to_global" to "提升为全局",
        "perm_rules_short" to "对话中选「始终允许」会按命令或路径生成记忆规则",
        "perm_safety_confirm_action" to "确认（%1${'d）}d）",
        "perm_safety_confirm_action_ready" to "确认",
        "perm_safety_confirm_message" to "禁用后，AUTO 模式下连灾难性删除也不再拦截，Shizuku 工具也不再逐次确认。请谨慎操作。",
        "perm_safety_confirm_title" to "确认禁用安全拦截？",
        "perm_safety_group" to "安全防护",
        "plan_mode_already_active" to "当前已处于 PLAN（计划）模式。",
        "plan_mode_approved_auto" to "计划已获批准，已恢复 AUTO 模式。按已批准的范围实施，工具调用遵守 AUTO 权限策略。",
        "plan_mode_approved_build" to "计划已获批准，当前为 BUILD 模式。按已批准的范围实施。",
        "plan_mode_enter_action" to "进入 PLAN 模式",
        "plan_mode_entered" to "已进入 PLAN（计划）模式。",
        "plan_mode_exit_action" to "退出 PLAN 模式",
        "plan_mode_exited" to "已退出 PLAN，恢复为 %1${'s 模式。}s 模式。",
        "plan_mode_external_auto" to "用户已切换到 AUTO 模式，后续操作遵守该模式权限和当前任务范围。",
        "plan_mode_external_build" to "用户已切换到 BUILD 模式，后续操作遵守该模式权限和当前任务范围。",
        "plan_mode_goal_active" to "目标正在自主运行，暂不支持进入或退出 PLAN 模式。",
        "plan_mode_invalid_action" to "无效的 action：%1${'s。支持 enter 或 exit。}s。支持 enter 或 exit。",
        "plan_mode_not_active" to "当前不在 PLAN 模式，无法退出。",
        "plan_mode_reason_unspecified" to "未提供理由",
        "plan_mode_refinement_requested" to "用户选择继续完善方案，当前保持 PLAN 模式。等待具体反馈后再调整，暂不重复申请退出。",
        "plan_mode_request_details" to "操作：%1${'s\n理由：%2$s}s\n理由：%2${'s}s",
        "plan_mode_request_summary" to "申请%1${'s}s",
        "plan_mode_request_title" to "切换计划模式",
        "plan_mode_session_missing" to "会话不存在，无法切换模式。",
        "plan_mode_session_required" to "未关联会话，无法切换模式。",
        "prompts_add_prompt" to "添加片段",
        "prompts_batch_delete_confirm" to "确定删除选中的 %1${'d 条自定义提示词吗？对应编号将回落到下一层来源。}d 条自定义提示词吗？对应编号将回落到下一层来源。",
        "prompts_batch_delete_title" to "删除提示词",
        "prompts_disable_builtin" to "完全禁用内置提示词",
        "prompts_disable_builtin_hint" to "只保留你的片段；需要自己补全全部规则，谨慎开启",
        "prompts_editor_edit" to "编辑提示词",
        "prompts_editor_new" to "新建提示词",
        "prompts_empty" to "暂无可用片段",
        "prompts_field_basic" to "基本信息",
        "prompts_field_content" to "片段内容",
        "prompts_field_content_placeholder" to "写模型要遵守的内容",
        "prompts_field_number" to "编号",
        "prompts_field_number_placeholder" to "两位数字，如 35",
        "prompts_field_summary" to "摘要（可选）",
        "prompts_field_summary_placeholder" to "简述片段内容，不会注入提示词",
        "prompts_field_title" to "名称",
        "prompts_field_title_placeholder" to "如：我的安全规则",
        "prompts_help" to "帮助",
        "prompts_reset_confirm" to "将删除当前项目与全局层里的所有自定义提示词，各编号回落到内置提示词。此操作不可撤销。",
        "prompts_reset_custom" to "重置自定义提示词",
        "prompts_sort_long_press" to "长按可调整顺序",
        "prompts_source_builtin" to "内置",
        "prompts_source_global" to "全局",
        "prompts_source_local" to "本地",
        "prompts_source_project" to "项目",
        "prompts_title" to "提示词",
        "provider_add" to "添加供应商",
        "provider_add_all_models" to "全部添加",
        "provider_add_all_models_confirm" to "确定将拉取到的 %1${'d 个模型全部添加至当前提供商？}d 个模型全部添加至当前提供商？",
        "provider_add_all_models_title" to "添加全部模型",
        "provider_add_model" to "添加模型",
        "provider_add_sheet_title" to "添加供应商",
        "provider_api_key" to "API Key",
        "provider_api_key_required" to "请先填写 API Key",
        "provider_base_url" to "Base URL",
        "provider_brand_doubao" to "豆包",
        "provider_brand_hunyuan" to "混元",
        "provider_brand_siliconflow" to "硅基流动",
        "provider_brand_tongyi_qianwen" to "通义千问",
        "provider_brand_zhipu" to "智谱",
        "provider_cache_anthropic_subtitle" to "为 system/工具/消息前缀打显式缓存断点，降低重复输入成本",
        "provider_cache_anthropic_title" to "Anthropic 缓存断点",
        "provider_cache_openai_chat_subtitle" to "为请求携带 prompt_cache_key 缓存键",
        "provider_cache_openai_chat_title" to "OpenAI 兼容缓存键",
        "provider_config" to "配置",
        "provider_custom" to "自定义供应商",
        "provider_dashboard_clear_script" to "不使用面板脚本 (清空)",
        "provider_dashboard_loading" to "正在加载面板…",
        "provider_dashboard_no_scripts" to "~/.aicode/scripts 目录下暂无脚本",
        "provider_dashboard_preview_title" to "面板预览",
        "provider_dashboard_raw_output" to "原始输出",
        "provider_dashboard_run_test" to "运行测试",
        "provider_dashboard_script" to "面板脚本",
        "provider_dashboard_script_desc" to "执行并预览面板",
        "provider_dashboard_script_placeholder" to "例如 demo_balance.py",
        "provider_dashboard_select_script" to "选择脚本",
        "provider_dashboard_select_title" to "选择面板脚本",
        "provider_dashboard_test_btn" to "运行测试",
        "provider_dashboard_test_failed" to "执行失败",
        "provider_edit" to "编辑供应商",
        "provider_edit_model" to "编辑模型",
        "provider_fetch_failed" to "拉取失败：%1${'s}s",
        "provider_fetch_http_error" to "HTTP %1${'d · %2$dms}d · %2${'dms}dms",
        "provider_fetch_missing_fields" to "响应缺少列表字段",
        "provider_fetch_missing_fields_detail" to "响应缺少列表字段 (models 或 data): %1${'s}s",
        "provider_fetch_models" to "拉取模型",
        "provider_fetch_success" to "成功拉取到 %1${'d 个模型 · %2$dms}d 个模型 · %2${'dms}dms",
        "provider_filter_models_hint" to "输入模型名称筛选",
        "provider_full_url" to "完整 URL",
        "provider_full_url_desc" to "Base URL 即完整请求地址，不自动拼接默认路径",
        "provider_headers_add" to "添加请求头",
        "provider_headers_count" to "已配置 %1${'d 个请求头}d 个请求头",
        "provider_headers_desc" to "完全覆盖该供应商请求的同名默认头",
        "provider_headers_empty" to "暂无请求头，点击下方添加",
        "provider_headers_manage" to "管理",
        "provider_headers_name" to "Header 名",
        "provider_headers_name_hint" to "例如 Authorization",
        "provider_headers_remove" to "删除请求头",
        "provider_headers_title" to "自定义请求头",
        "provider_headers_value" to "值",
        "provider_headers_value_hint" to "如 Bearer sk-xxx",
        "provider_hide_api_key" to "隐藏密钥",
        "provider_interactions_api" to "Interactions API (新版)",
        "provider_model_already_added" to "该模型已添加",
        "provider_model_cap_image_output" to "图片输出",
        "provider_model_cap_vision" to "图片输入",
        "provider_model_capabilities" to "能力",
        "provider_model_capability_reasoning" to "推理能力",
        "provider_model_capability_tools" to "工具调用",
        "provider_model_context_input" to "输入窗口 (Token)",
        "provider_model_context_output" to "输出窗口 (Token)",
        "provider_model_context_total" to "总上下文窗口 (Token)",
        "provider_model_icon" to "模型图标",
        "provider_model_name" to "模型名称",
        "provider_model_price_cache_read" to "缓存读取价格 (USD/1M tokens)",
        "provider_model_price_cache_write" to "缓存写入价格 (USD/1M tokens)",
        "provider_model_price_input" to "输入价格 (USD/1M tokens)",
        "provider_model_price_output" to "输出价格 (USD/1M tokens)",
        "provider_model_reasoning_can_disable" to "可以关闭思考",
        "provider_model_reasoning_hint" to "只勾选该模型真实支持的档位；全部不勾则由内置能力表自动判断",
        "provider_model_section_price" to "价格",
        "provider_model_section_reasoning" to "思考档位",
        "provider_model_window_invalid" to "请输入有效的正整数，或留空使用自动识别值",
        "provider_models_aligned_removed" to "已移除 %1${'d 个远端已不存在的模型}d 个远端已不存在的模型",
        "provider_models_batch_delete" to "删除",
        "provider_models_batch_delete_confirm" to "确定删除选中的 %1${'d 个模型吗？}d 个模型吗？",
        "provider_models_batch_delete_title" to "删除模型",
        "provider_models_batch_test" to "测试",
        "provider_models_count" to "模型（%1${'s）}s）",
        "provider_models_count_tag" to "%1${'d 个模型}d 个模型",
        "provider_models_deselect_all" to "取消全选",
        "provider_models_select" to "多选",
        "provider_models_select_all" to "全选",
        "provider_models_selected_count" to "已选 %1${'d 项}d 项",
        "provider_multi_key_add" to "添加 Key",
        "provider_multi_key_cooldown" to "冷却时长",
        "provider_multi_key_cooldown_desc" to "被切走的 Key 隔多久重新纳入候选",
        "provider_multi_key_cooldown_off" to "不冷却",
        "provider_multi_key_cooldown_value" to "%1${'d 分钟}d 分钟",
        "provider_multi_key_empty" to "点此添加第一个 Key",
        "provider_multi_key_failover" to "失败切换",
        "provider_multi_key_item" to "密钥 #%1${'d}d",
        "provider_multi_key_list" to "Key 列表",
        "provider_multi_key_manage" to "多 Key 管理",
        "provider_multi_key_status_codes" to "自动切换状态码",
        "provider_multi_key_status_codes_hint" to "逗号分隔，留空使用默认 401,402,403,429",
        "provider_multi_key_strategy" to "取用策略",
        "provider_multi_key_strategy_round_robin" to "轮询",
        "provider_multi_key_strategy_round_robin_desc" to "新会话轮流起步以摊平额度；同一会话内不换 Key，保持缓存命中",
        "provider_multi_key_strategy_sequential" to "顺序",
        "provider_multi_key_strategy_sequential_desc" to "始终用靠前的 Key，失败达阈值才切下一个",
        "provider_multi_key_subtitle" to "同一供应商下配多个 Key，密钥不可用时自动切换并重发",
        "provider_multi_key_summary" to "%1${'d 个 · %2$s}d 个 · %2${'s}s",
        "provider_multi_key_title" to "多 Key 模式",
        "provider_multi_key_weight" to "调度权重",
        "provider_multi_key_weight_hint" to "默认 1 · ≤ 0 时不参与 · 最大 1,000,000",
        "provider_new" to "新供应商",
        "provider_no_matching_models" to "没有匹配的模型",
        "provider_no_models" to "暂无模型",
        "provider_no_models_hint" to "点击右上方按钮添加模型",
        "provider_please_wait" to "请稍候…",
        "provider_preset_empty" to "未找到匹配的预设",
        "provider_preset_loading" to "正在加载预设库…",
        "provider_proxy_enable" to "为该供应商单独配置代理",
        "provider_proxy_enable_desc" to "开启后该供应商的请求走专属代理；关闭则跟随全局代理",
        "provider_proxy_off" to "未启用",
        "provider_proxy_section" to "代理（可选）",
        "provider_register_now" to "立即注册",
        "provider_response_api" to "Response API (新版)",
        "provider_script_param_add" to "添加参数",
        "provider_script_param_key" to "参数名 (KEY)",
        "provider_script_param_key_hint" to "例如 ACCOUNT_ID",
        "provider_script_param_manage" to "管理",
        "provider_script_param_remove" to "删除参数",
        "provider_script_param_value" to "参数值",
        "provider_script_param_value_hint" to "如 acc_12345",
        "provider_script_params_count" to "已配置 %1${'d 个参数}d 个参数",
        "provider_script_params_desc" to "以 AICODE_KEY_ 注入面板脚本环境变量",
        "provider_script_params_empty" to "暂无参数，点击下方添加",
        "provider_script_params_title" to "脚本参数",
        "provider_section_basic" to "基本信息",
        "provider_section_dashboard" to "自定义面板",
        "provider_section_options" to "选项",
        "provider_section_type" to "上游格式",
        "provider_show_api_key" to "显示密钥",
        "provider_sort_long_press" to "长按拖动排序",
        "provider_test" to "测试",
        "provider_test_detail_title" to "测试调试信息",
        "provider_test_error_detail" to "错误堆栈",
        "provider_test_req_body" to "请求体",
        "provider_test_req_headers" to "请求头",
        "provider_test_req_url" to "请求 URL",
        "provider_test_resp_body" to "响应体",
        "provider_test_resp_headers" to "响应头",
        "provider_test_success" to "连接正常 · %1${'dms}dms",
        "providers_batch_delete_confirm" to "确定删除选中的 %1${'d 个提供商吗？此操作不可恢复。}d 个提供商吗？此操作不可恢复。",
        "providers_batch_delete_title" to "删除提供商",
        "providers_empty" to "还没有供应商，点右上角 + 添加",
        "proxy_auth" to "认证（可选）",
        "proxy_enable" to "启用代理",
        "proxy_enable_subtitle" to "开启后应用网络请求与容器内命令统一走代理",
        "proxy_host" to "服务器地址",
        "proxy_host_placeholder" to "如 127.0.0.1",
        "proxy_no_proxy" to "不走代理的地址",
        "proxy_no_proxy_hint" to "逗号分隔；支持通配符 *、host:port、CIDR 网段",
        "proxy_password" to "密码（可选）",
        "proxy_port" to "端口",
        "proxy_port_placeholder" to "如 7890",
        "proxy_server" to "代理服务器",
        "proxy_test" to "测试代理",
        "proxy_test_connect_failed" to "连接代理失败：%1${'s}s",
        "proxy_test_http_error" to "代理返回异常状态 HTTP %1${'d：%2$s}d：%2${'s}s",
        "proxy_test_invalid_config" to "请先填写有效的服务器地址与端口（1-65535）",
        "proxy_test_ok" to "代理可用（HTTP %1${'d）}d）",
        "proxy_test_title" to "连通性测试",
        "proxy_test_url_label" to "测试地址",
        "proxy_test_url_placeholder" to "https://www.google.com",
        "proxy_testing" to "测试中…",
        "proxy_title" to "网络代理",
        "proxy_type" to "代理类型",
        "proxy_type_http" to "HTTP",
        "proxy_type_socks5" to "SOCKS5",
        "proxy_username" to "账号（可选）",
        "remote_add_channel_first" to "请先在「连接配置」中添加一个远程通道。",
        "remote_add_connection" to "添加连接通道",
        "remote_add_workspace" to "添加挂载目录",
        "remote_auth_key" to "密钥",
        "remote_auth_password" to "密码",
        "remote_auto_connect_and_sync" to "应用启动时自动连接并同步该工作区",
        "remote_auto_connect_on_start" to "应用启动时自动连接",
        "remote_browse_dir" to "浏览目录",
        "remote_clear_index_done" to "已清除同步记录",
        "remote_confirm_select" to "确认选择",
        "remote_connect_failed" to "连接失败：%1${'s}s",
        "remote_connect_success" to "连接成功",
        "remote_connection_name_hint" to "连接名称",
        "remote_connection_sort_long_press" to "长按拖动排序",
        "remote_current_path" to "当前路径：%1${'s}s",
        "remote_delete_connection_confirm" to "确定删除连接「%1${'s」吗？使用该连接的同步配置将不可用。}s」吗？使用该连接的同步配置将不可用。",
        "remote_delete_mount_confirm" to "确定删除工作区映射「%1${'s」吗？本地与远程文件不会被删除。}s」吗？本地与远程文件不会被删除。",
        "remote_download_all_failed" to "全量下载失败：%1${'s}s",
        "remote_download_all_success" to "全量下载完成",
        "remote_edit_connection" to "编辑连接通道",
        "remote_edit_workspace" to "编辑挂载目录",
        "remote_go_add" to "去添加",
        "remote_hint_title" to "提示",
        "remote_host_address" to "主机地址",
        "remote_key_passphrase" to "口令（可选）",
        "remote_link_channel" to "关联远程连接通道",
        "remote_load_failed" to "加载失败：%1${'s}s",
        "remote_login_key" to "登录密钥",
        "remote_map_to_local" to "映射到本地工作区",
        "remote_mount_already_exists" to "该挂载已存在：相同的通道、远程目录与工作区组合",
        "remote_no_connections" to "暂无连接配置",
        "remote_no_connections_desc" to "添加 SFTP / FTP 连接",
        "remote_no_keys_hint" to "暂无密钥，请先到「SSH 主机密钥」页添加",
        "remote_no_local_workspace" to "暂无本地工作区，请先在侧边栏创建一个。",
        "remote_no_workspaces" to "暂无挂载目录",
        "remote_no_workspaces_desc" to "本地改动自动同步到远程，远程文件可手动下载",
        "remote_parent_dir" to ".. (上一级)",
        "remote_password" to "密码",
        "remote_port" to "端口",
        "remote_select_channel" to "选择远程通道",
        "remote_select_key" to "选择密钥",
        "remote_select_local_workspace" to "选择本地工作区",
        "remote_select_remote_dir" to "选择远程目录",
        "remote_tab_connections" to "连接配置",
        "remote_tab_ftp" to "内置FTP",
        "remote_tab_mounts" to "挂载目录",
        "remote_tab_sync" to "同步设置",
        "remote_target_dir" to "远程目标目录",
        "remote_test_connection" to "测试连通性",
        "remote_toggle_password" to "切换密码可见性",
        "remote_unknown_error" to "未知错误",
        "remote_upload_failed" to "上传失败：%1${'s}s",
        "remote_upload_full_failed" to "全量上传失败：%1${'s}s",
        "remote_upload_full_success" to "全量上传完成",
        "remote_upload_success" to "上传完成",
        "remote_workspace_title" to "连接与同步",
        "retry_error_connection_refused" to "连接被拒绝",
        "retry_error_connection_reset" to "连接中断",
        "retry_error_dns_failed" to "DNS 解析失败",
        "retry_error_network" to "网络连接断开",
        "retry_error_rate_limit" to "速率限制",
        "retry_error_server" to "服务端错误",
        "retry_error_server_overloaded" to "服务器负载过高",
        "retry_error_ssl" to "SSL 握手失败",
        "retry_error_timeout" to "连接超时",
        "retry_error_unknown" to "网络异常",
        "retry_error_with_code" to "%1${'s (%2$d)}s (%2${'d)}d)",
        "session_group_last_30_days" to "30天内",
        "session_group_last_7_days" to "7天内",
        "session_group_month_format" to "yyyy年M月",
        "session_group_pinned" to "置顶",
        "session_group_today" to "今天",
        "session_group_yesterday" to "昨天",
        "settings_about" to "关于",
        "settings_add_mcp_server" to "添加 MCP 服务器",
        "settings_add_provider" to "添加供应商",
        "settings_agent_sound_subtitle" to "退到后台时 Agent 完成弹出通知",
        "settings_agent_sound_title" to "Agent 完成通知",
        "settings_app_permissions" to "软件权限",
        "settings_approval_notification_subtitle" to "AI 请求授权、提问或等待计划审批时弹出通知",
        "settings_approval_notification_title" to "授权审批通知",
        "settings_auto_remove_stale_models" to "拉取模型后自动移除无效模型",
        "settings_auto_remove_stale_models_desc" to "拉取成功后，移除远端已不存在的本地模型条目",
        "settings_autostart_subtitle" to "允许系统在后台自动拉起应用",
        "settings_autostart_title" to "自启动管理",
        "settings_background_image" to "背景图",
        "settings_background_image_alpha" to "透明度",
        "settings_background_image_none" to "未设置",
        "settings_background_image_pick" to "选择图片",
        "settings_background_image_remove" to "移除背景",
        "settings_backup" to "备份与还原",
        "settings_category_ai" to "AI 配置",
        "settings_category_background" to "后台运行",
        "settings_category_data" to "数据与诊断",
        "settings_category_environment" to "运行环境",
        "settings_category_general" to "通用设置",
        "settings_category_help" to "帮助与关于",
        "settings_category_permissions" to "权限与后台",
        "settings_category_system_permissions" to "系统权限",
        "settings_compaction_follow_chat" to "跟随当前聊天模型",
        "settings_compaction_model" to "压缩模型",
        "settings_compaction_threshold" to "自动压缩阈值",
        "settings_compaction_threshold_desc" to "上下文占用达到窗口的该比例时自动压缩",
        "settings_compaction_threshold_input_hint" to "填 1-100 的整数百分比",
        "settings_container" to "容器与镜像",
        "settings_default_models" to "默认模型",
        "settings_delete_external_workspace_sessions" to "删除外部工作区聊天记录",
        "settings_delete_external_workspace_sessions_desc" to "移除外部本地工作区时，一并删除该工作区的聊天记录",
        "settings_enter_to_send" to "回车键发送消息",
        "settings_enter_to_send_desc" to "关闭后回车换行，需点发送按钮发送",
        "settings_first_byte_timeout" to "首字节超时",
        "settings_first_byte_timeout_desc" to "等待首个响应块的最长时间",
        "settings_general" to "偏好设置",
        "settings_general_goal" to "目标（goal）",
        "settings_general_input" to "输入",
        "settings_general_models" to "模型",
        "settings_general_network" to "网络",
        "settings_general_session" to "会话",
        "settings_general_tools" to "工具",
        "settings_goal_max_tokens" to "token 预算",
        "settings_goal_max_tokens_desc" to "自主循环累计消耗的输入+输出 token 上限",
        "settings_goal_max_tokens_input_hint" to "填不小于 1000 的整数",
        "settings_goal_max_turns" to "最大续跑轮数",
        "settings_goal_max_turns_desc" to "自主循环最多续跑多少轮，达到即停止",
        "settings_goal_max_turns_input_hint" to "填大于 0 的整数轮数",
        "settings_image_gen_model" to "生图模型",
        "settings_image_gen_unconfigured" to "未配置",
        "settings_keepalive_subtitle" to "显示前台通知，防止后台被杀",
        "settings_keepalive_title" to "后台运行保活",
        "settings_language" to "语言",
        "settings_live_notification_subtitle" to "AI 在后台工作时展示实时进度通知",
        "settings_live_notification_title" to "实时通知",
        "settings_log" to "日志",
        "settings_log_no_files" to "还没有日志文件",
        "settings_log_read_failed" to "读取日志失败：%1${'s}s",
        "settings_max_network_retries" to "重试次数",
        "settings_max_network_retries_desc" to "网络失败后的指数退避重试次数",
        "settings_mb_value" to "%1${'d MB}d MB",
        "settings_mcp" to "MCP 服务器",
        "settings_models_fetch_failed" to "拉取失败",
        "settings_network_guide_brand_huawei" to "华为 / 荣耀：应用启动管理 → 手动管理 → 允许自启动、关联启动、后台活动",
        "settings_network_guide_brand_oppo" to "OPPO / 一加 / realme：应用管理 → 电池使用 → 允许后台活动；电池 → 更多设置 → 优化电池使用 → 不优化",
        "settings_network_guide_brand_samsung" to "三星：电池和设备维护 → 电池 → 后台使用限制 → 加入「从不休眠」；应用 → 电池 → 不受限制",
        "settings_network_guide_brand_vivo" to "vivo / iQOO：电池 → 后台耗电管理 → 允许后台高耗电；应用与权限 → 自启动",
        "settings_network_guide_brand_xiaomi" to "小米 / Redmi：省电与电池 → 应用智能省电 → 无限制",
        "settings_network_guide_intro" to "若切到后台后出现「网络连接断开」，请按以下顺序设置：",
        "settings_network_guide_more" to "更多机型与详细说明见官网文档",
        "settings_network_guide_step_battery" to "3. 在系统设置里把本应用的省电策略设为「无限制 / 不优化」，并允许自启动",
        "settings_network_guide_step_keepalive" to "1. 打开上方「后台运行保活」，并保留通知栏的常驻通知",
        "settings_network_guide_step_lock" to "2. 在多任务界面给本应用加锁，不要直接划掉",
        "settings_network_guide_subtitle" to "切到后台后断网？按这里逐项检查",
        "settings_network_guide_title" to "后台断网排查",
        "settings_percent_value" to "%1${'d%%}d%%",
        "settings_permission_apk_install" to "安装未知应用",
        "settings_permission_apk_install_desc" to "允许安装 APK 安装包",
        "settings_permission_battery" to "忽略电池优化",
        "settings_permission_battery_desc" to "允许后台持续运行，减少被杀",
        "settings_permission_denied" to "未允许",
        "settings_permission_granted" to "已允许",
        "settings_permission_notification_denied_desc" to "后台保活与 Agent 完成通知需要通知权限才能生效，当前无法弹出系统授权框，请在系统设置中手动开启",
        "settings_permission_notification_title" to "需要通知权限",
        "settings_permission_storage" to "访问存储空间",
        "settings_permission_storage_denied_desc" to "存储权限已被拒绝且无法再次申请，请在系统设置中手动开启",
        "settings_permission_storage_desc" to "读写工作区文件与容器镜像",
        "settings_permissions" to "工具授权",
        "settings_provider_editor" to "供应商",
        "settings_providers" to "AI 供应商",
        "settings_reconnect" to "重新连接",
        "settings_refresh_logs" to "刷新日志",
        "settings_remote_servers" to "连接与同步",
        "settings_rerun_onboarding" to "重新运行新手引导",
        "settings_retries_count" to "%1${'d 次}d 次",
        "settings_retries_input_hint" to "填 0 表示不重试",
        "settings_screen_on_subtitle" to "App 在前台时不自动息屏",
        "settings_screen_on_title" to "屏幕常亮",
        "settings_select_section_hint" to "从左侧选择一个设置项",
        "settings_sendfile_max_size" to "sendFile 单文件上限",
        "settings_sendfile_max_size_desc" to "单个文件大小上限，超过则整批发送失败",
        "settings_sendfile_max_size_input_hint" to "单位 MB，填大于 0 的整数",
        "settings_shizuku" to "Shizuku",
        "settings_shizuku_hint_denied" to "点击申请 Shizuku 授权",
        "settings_shizuku_hint_not_installed" to "点击前往下载安装 Shizuku",
        "settings_shizuku_hint_not_running" to "点击打开 Shizuku 并启动服务",
        "settings_shizuku_hint_ready" to "已获授权，AI 可通过 adb shell 执行命令",
        "settings_shizuku_status_denied" to "未授权",
        "settings_shizuku_status_not_installed" to "未安装",
        "settings_shizuku_status_not_running" to "服务未运行",
        "settings_shizuku_status_ready" to "已就绪",
        "settings_skills" to "技能",
        "settings_startup_session" to "启动时会话",
        "settings_startup_session_new" to "新开会话",
        "settings_startup_session_new_desc" to "复用尚未发送过消息的空会话，否则新建",
        "settings_startup_session_recent" to "打开最近会话",
        "settings_startup_session_recent_desc" to "直接进入最近更新的会话",
        "settings_storage" to "存储空间",
        "settings_stream_idle_timeout" to "数据块间隔超时",
        "settings_stream_idle_timeout_desc" to "两个响应块之间的最长等待",
        "settings_subagent_tier_light" to "轻量",
        "settings_subagent_tier_standard" to "均衡",
        "settings_subagent_tier_strong" to "强力",
        "settings_subagent_tier_unconfigured" to "未配置",
        "settings_subagent_tiers_header" to "子代理模型档位",
        "settings_subagents" to "子代理",
        "settings_theme_title" to "外观主题",
        "settings_timeout_input_hint" to "单位秒，填 0 表示不限制",
        "settings_timeout_seconds" to "%1${'d 秒}d 秒",
        "settings_timeout_unlimited" to "不限制",
        "settings_title" to "设置",
        "settings_title_follow_chat" to "跟随当前聊天模型",
        "settings_title_model" to "标题总结模型",
        "settings_token_stats_cache_hit_rate" to "缓存命中率",
        "settings_token_stats_cache_read_write" to "读取 %1${'s / 写入 %2$s}s / 写入 %2${'s}s",
        "settings_token_stats_calls_suffix" to "次调用",
        "settings_token_stats_calls_suffix2" to "%1${'d 次调用}d 次调用",
        "settings_token_stats_col_cache_hit_rate" to "命中率",
        "settings_token_stats_col_cached_read" to "缓存读取",
        "settings_token_stats_col_cached_write" to "缓存写入",
        "settings_token_stats_col_cost" to "费用",
        "settings_token_stats_col_duration" to "耗时",
        "settings_token_stats_col_input" to "输入",
        "settings_token_stats_col_model" to "模型",
        "settings_token_stats_col_output" to "输出",
        "settings_token_stats_col_provider" to "供应商",
        "settings_token_stats_col_reasoning_effort" to "思考强度",
        "settings_token_stats_col_reasoning_wire" to "实际发出",
        "settings_token_stats_col_status" to "状态",
        "settings_token_stats_col_time" to "时间",
        "settings_token_stats_col_ttfb" to "首字节",
        "settings_token_stats_cost" to "费用",
        "settings_token_stats_filter_all_models" to "全部模型",
        "settings_token_stats_filter_all_providers" to "全部供应商",
        "settings_token_stats_filter_clear" to "清除筛选",
        "settings_token_stats_filter_model" to "模型：%1${'s}s",
        "settings_token_stats_filter_provider" to "供应商：%1${'s}s",
        "settings_token_stats_legend_cached" to "缓存",
        "settings_token_stats_legend_input" to "输入",
        "settings_token_stats_legend_output" to "输出",
        "settings_token_stats_model" to "模型统计",
        "settings_token_stats_no_data" to "暂无数据",
        "settings_token_stats_page" to "第 %1${'d / %2$d 页}d / %2${'d 页}d 页",
        "settings_token_stats_period_30d" to "近30天",
        "settings_token_stats_period_7d" to "近7天",
        "settings_token_stats_period_all" to "全部",
        "settings_token_stats_period_today" to "今天",
        "settings_token_stats_provider" to "供应商统计",
        "settings_token_stats_provider_keys" to "%1${'d 个密钥}d 个密钥",
        "settings_token_stats_recent_calls" to "调用记录",
        "settings_token_stats_requests" to "请求次数",
        "settings_token_stats_requests_unit" to "次请求",
        "settings_token_stats_reset" to "重置",
        "settings_token_stats_reset_confirm" to "将清空全部调用记录，此操作不可恢复。确定重置？",
        "settings_token_stats_reset_title" to "重置 Token 统计",
        "settings_token_stats_retries" to "重试 %1${'d 次}d 次",
        "settings_token_stats_success_rate" to "成功率 %1${'s}s",
        "settings_token_stats_title" to "Token 统计",
        "settings_token_stats_total" to "总消耗",
        "settings_token_stats_trend" to "消耗趋势",
        "settings_token_stats_unknown" to "未知",
        "settings_tokens_value" to "%1${'d}d",
        "settings_turns_value" to "%1${'d 轮}d 轮",
        "settings_user_guide" to "使用手册",
        "settings_vision_follow_chat" to "跟随当前聊天模型",
        "settings_vision_model" to "识图模型",
        "skills_add" to "添加技能",
        "skills_add_from_file" to "从文件导入",
        "skills_add_from_file_desc" to "选择含指令正文的 .md 文件",
        "skills_add_from_zip" to "从压缩包导入",
        "skills_add_from_zip_desc" to "选择 .zip，可含多个技能",
        "skills_add_manual" to "手动新建",
        "skills_add_manual_desc" to "填写名称与正文，从头新建一个技能",
        "skills_add_sheet_title" to "添加技能",
        "skills_batch_delete_confirm" to "将删除选中的 %1${'d 个技能目录，此操作不可恢复。确定删除？}d 个技能目录，此操作不可恢复。确定删除？",
        "skills_batch_delete_title" to "删除技能",
        "skills_delete_confirm_message" to "将删除「%1${'s」的技能目录，此操作不可恢复。确定删除？}s」的技能目录，此操作不可恢复。确定删除？",
        "skills_delete_confirm_title" to "删除技能",
        "skills_description" to "描述",
        "skills_description_hint" to "简述何时该用它，AI 据此判断要不要加载",
        "skills_edit" to "编辑技能",
        "skills_editor_basic" to "基本信息",
        "skills_editor_edit" to "编辑技能",
        "skills_editor_error_empty_instructions" to "指令正文不能为空",
        "skills_editor_error_invalid_name" to "名称不合法：不能为空，也不能包含路径分隔符等特殊字符",
        "skills_editor_error_io" to "写入技能文件失败，请稍后重试",
        "skills_editor_error_name_conflict" to "该作用域下已存在同名技能",
        "skills_editor_new" to "新建技能",
        "skills_editor_scope" to "作用域",
        "skills_empty" to "还没有安装技能",
        "skills_empty_hint" to "点右上角「+」新建或导入",
        "skills_enable" to "是否启用",
        "skills_import_error_empty_content" to "内容为空",
        "skills_import_error_invalid_archive" to "压缩包无法读取，请确认是有效的 .zip",
        "skills_import_error_invalid_name" to "名称不合法",
        "skills_import_error_io" to "写入技能文件失败",
        "skills_import_error_name_conflict" to "已存在同名技能",
        "skills_import_error_no_skill" to "压缩包里没有找到技能",
        "skills_import_error_unsupported_file" to "文件类型不支持",
        "skills_import_failure_line" to "%1${'s：%2$s}s：%2${'s}s",
        "skills_import_partial" to "已导入 %1${'d 个，%2$d 个跳过}d 个，%2${'d 个跳过}d 个跳过",
        "skills_import_result_title" to "导入结果",
        "skills_import_running" to "正在导入…",
        "skills_import_success" to "已导入 %1${'d 个技能}d 个技能",
        "skills_instructions" to "指令正文",
        "skills_instructions_hint" to "写清这类任务的标准流程与注意事项，支持 Markdown",
        "skills_name_hint" to "建议用英文小写，如 pdf-report",
        "skills_no_global_skills" to "暂无全局技能",
        "skills_no_project_skills" to "当前项目暂无技能",
        "skills_scope_global" to "全局",
        "skills_scope_project" to "当前项目",
        "skills_summary" to "摘要",
        "slash_command_compress_desc" to "手动触发当前会话的上下文压缩",
        "slash_command_goal_desc" to "设定会话持续目标，AI 自主循环推进至达成或预算耗尽",
        "slash_command_init_desc" to "分析当前工作区代码库，生成或改进项目规则 AGENTS.md",
        "slash_command_usage_desc" to "查看今日与累计的调用次数、token 用量与预估费用",
        "ssh_host_key_changed_title" to "SSH 主机密钥已变化",
        "ssh_host_key_confirm_title" to "确认 SSH 主机密钥",
        "ssh_host_key_detail" to "已保存的服务器主机指纹：\n%1${'s}s",
        "ssh_host_key_empty" to "暂无已保存的主机指纹",
        "ssh_host_key_fingerprint_value" to "指纹：%1${'s}s",
        "ssh_host_key_reject" to "拒绝",
        "ssh_host_key_remove" to "删除指纹",
        "ssh_host_key_settings_desc" to "已确认的主机指纹会用于阻止中间人攻击。服务器重装或密钥变化后，请先核实再重新确认。",
        "ssh_host_key_settings_title" to "SSH 主机密钥",
        "ssh_host_key_tab_fingerprints" to "指纹",
        "ssh_host_key_tab_keys" to "密钥",
        "ssh_host_key_trust" to "信任并连接",
        "ssh_login_key_add" to "添加密钥",
        "ssh_login_key_add_from_file" to "从文件导入",
        "ssh_login_key_add_from_file_desc" to "选择设备上的私钥文件",
        "ssh_login_key_add_from_paste" to "粘贴密钥",
        "ssh_login_key_add_from_paste_desc" to "直接粘贴私钥内容",
        "ssh_login_key_add_method_title" to "添加登录密钥",
        "ssh_login_key_content_label" to "私钥内容",
        "ssh_login_key_edit_title" to "编辑密钥",
        "ssh_login_key_empty" to "暂无已保存的登录密钥",
        "ssh_login_key_encrypted" to "加密密钥（需口令）",
        "ssh_login_key_fingerprint_label" to "公钥指纹",
        "ssh_login_key_name_label" to "名称",
        "ssh_login_key_passphrase_placeholder" to "私钥未加密则留空",
        "ssh_login_key_paste_content_placeholder" to "-----BEGIN OPENSSH PRIVATE KEY-----",
        "ssh_login_key_paste_invalid" to "私钥内容无效，请检查后重试",
        "ssh_login_key_paste_name_placeholder" to "留空则自动命名",
        "ssh_login_key_paste_title" to "粘贴密钥",
        "ssh_login_key_toggle_content" to "显示或隐藏私钥内容",
        "status_connected" to "已连接",
        "status_connection_failed" to "连接失败",
        "status_disconnected" to "未连接",
        "status_reconnecting" to "重连中",
        "storage_breakdown_note" to "点击带箭头的分类可展开明细。删会话可减小聊天记录，容器与工作区请在各自的页面重置或删除；外部目录工作区位于设备存储，未计入。",
        "storage_category_ai_config" to "AI 配置与产物",
        "storage_category_apk" to "应用安装包",
        "storage_category_caches" to "缓存",
        "storage_category_chat" to "聊天记录",
        "storage_category_checkpoints" to "检查点",
        "storage_category_container" to "容器",
        "storage_category_container_images" to "容器镜像包",
        "storage_category_logs" to "日志",
        "storage_category_other" to "其他数据",
        "storage_category_workspaces" to "工作区",
        "storage_chat_messages" to "%1${'d 条消息}d 条消息",
        "storage_clean" to "清理",
        "storage_clean_caches" to "缓存文件",
        "storage_clean_caches_desc" to "临时文件，删除后会自动重建",
        "storage_clean_confirm_selected" to "将清理以下选中项，共约 %1${'s。此操作不可恢复。}s。此操作不可恢复。",
        "storage_clean_confirm_title" to "确认清理",
        "storage_clean_logs" to "运行日志",
        "storage_clean_logs_desc" to "应用日志与 AI 请求日志，删除不影响功能",
        "storage_clean_scope" to "仅清理本机应用缓存、日志及全局目录中的所选数据，可能影响多个工作区的历史输出读取或识图追问；不会删除聊天记录、工作区文件、容器、AI 配置或远程服务器数据。",
        "storage_clean_selected" to "清理选中项 · %1${'s}s",
        "storage_clean_tool_output" to "工具输出存档",
        "storage_clean_tool_output_desc" to "AI 超长工具输出保存到磁盘的副本，删除后旧消息里的输出路径不再可读",
        "storage_clean_vision" to "识图会话缓存",
        "storage_clean_vision_desc" to "识图追问的历史，删除后无法继续追问旧图片",
        "storage_device_free" to "设备可用 %1${'s / 共 %2$s}s / 共 %2${'s}s",
        "storage_freed" to "已释放 %1${'s}s",
        "storage_logs_ai" to "AI 请求日志",
        "storage_logs_app" to "应用日志",
        "storage_refresh" to "重新统计",
        "storage_scanning" to "统计中…",
        "storage_section_breakdown" to "占用明细",
        "storage_total_title" to "应用占用",
        "storage_untitled_session" to "未命名会话",
        "storage_workspace_attachments" to "含附件 %1${'s}s",
        "subagent_add" to "新建子代理",
        "subagent_all_tools" to "继承全量工具（不含 task）",
        "subagent_card_changed_files" to "改动 %1${'d 个文件}d 个文件",
        "subagent_card_collapse" to "收起摘要",
        "subagent_card_default_agent" to "子代理",
        "subagent_card_expand" to "展开摘要",
        "subagent_card_open" to "进入会话",
        "subagent_card_status_completed" to "已完成",
        "subagent_card_status_dispatched" to "已派发",
        "subagent_card_status_failed" to "失败",
        "subagent_card_status_running" to "运行中",
        "subagent_card_status_stopped" to "已停止",
        "subagent_card_stop" to "停止",
        "subagent_description_hint" to "简述何时该派它，主会话 AI 据此选择",
        "subagent_dispatch_count" to "已派发 %1${'d 个任务}d 个任务",
        "subagent_dispatch_title" to "子代理任务",
        "subagent_edit" to "编辑子代理",
        "subagent_editor_basic" to "基本信息",
        "subagent_editor_clear" to "清空",
        "subagent_editor_description" to "用途描述",
        "subagent_editor_edit" to "编辑子代理",
        "subagent_editor_effort" to "思考强度",
        "subagent_editor_error_empty_prompt" to "提示词正文不能为空",
        "subagent_editor_error_invalid_name" to "名称不合法：不能为空，也不能包含路径分隔符等特殊字符",
        "subagent_editor_error_io" to "写入定义文件失败，请稍后重试",
        "subagent_editor_error_name_conflict" to "该作用域下已存在同名子代理",
        "subagent_editor_mode" to "默认模式",
        "subagent_editor_model_group" to "模型",
        "subagent_editor_new" to "新建子代理",
        "subagent_editor_none" to "无",
        "subagent_editor_scope" to "作用域",
        "subagent_editor_selected_count" to "已选 %1${'d 个}d 个",
        "subagent_editor_tools_allow" to "工具白名单",
        "subagent_editor_tools_allow_hint" to "不选则开放全部工具（不含 task）",
        "subagent_editor_tools_deny" to "工具黑名单",
        "subagent_editor_tools_deny_hint" to "优先于白名单生效",
        "subagent_editor_tools_search" to "搜索工具",
        "subagent_enable" to "启用该子代理",
        "subagent_enable_hint" to "关闭后 AI 不再能派发它，已在运行的子会话不受影响",
        "subagent_inherit_parent" to "继承父会话",
        "subagent_inject" to "注入的提示词",
        "subagent_inject_base" to "子代理基线",
        "subagent_inject_base_hint" to "工具用法与路径约定，轻量、节省 Token",
        "subagent_inject_main_rules" to "完整通用规则",
        "subagent_inject_main_rules_hint" to "与「子代理基线」二选一即可；两个都开会重复注入规则、多占上下文",
        "subagent_inject_memory" to "记忆摘要",
        "subagent_inject_memory_hint" to "注入全局与项目长期记忆",
        "subagent_inject_none" to "不注入任何额外提示词",
        "subagent_inject_project_rules" to "项目规则",
        "subagent_inject_project_rules_hint" to "注入 AGENTS.md 或 CLAUDE.md",
        "subagent_inject_skills" to "技能清单",
        "subagent_inject_skills_hint" to "允许它加载并执行项目技能",
        "subagent_model" to "模型",
        "subagent_name_hint" to "建议用英文小写，如 researcher",
        "subagent_no_models" to "暂无可用模型。请先在「AI 供应商」中添加并配置模型。",
        "subagent_prompt" to "agent 提示词",
        "subagent_prompt_hint" to "写清它的角色、工作方式与产出要求",
        "subagent_scope_global" to "全局",
        "subagent_scope_project" to "当前项目",
        "subagent_source_file" to "定义文件",
        "subagent_tab_sessions" to "会话",
        "subagent_tools" to "工具集",
        "subagent_tools_allow" to "仅允许：%1${'s}s",
        "subagent_tools_deny" to "禁用：%1${'s}s",
        "subagents_batch_delete_confirm" to "将删除选中的 %1${'d 个子代理定义文件，此操作不可恢复。确定删除？}d 个子代理定义文件，此操作不可恢复。确定删除？",
        "subagents_batch_delete_title" to "删除子代理",
        "subagents_delete_confirm_message" to "将删除「%1${'s」的定义文件，此操作不可恢复。确定删除？}s」的定义文件，此操作不可恢复。确定删除？",
        "subagents_delete_confirm_title" to "删除子代理",
        "subagents_empty" to "还没有自定义子代理",
        "subagents_empty_hint" to "点右上角「+」新建一个，或把 agent 的 .md 定义放进 ~/.aicode/agents/",
        "subagents_no_global" to "暂无全局子代理",
        "subagents_no_project" to "当前项目暂无子代理",
        "sync_batch_size_desc" to "超出此数量的操作将分批排队执行，防止突发大量修改导致断连。",
        "sync_clear_index" to "清除同步记录",
        "sync_connect_and_sync" to "连接并同步",
        "sync_disconnect" to "断开连接",
        "sync_download_all" to "下载全部",
        "sync_follow_gitignore" to "遵循 .gitignore 规则",
        "sync_gitignore_desc" to "自动读取项目根目录的 .gitignore 并忽略其中内容。",
        "sync_max_batch_count" to "最大数量",
        "sync_max_batch_size" to "单次同步队列上限",
        "sync_path_mapping" to "%1${'s → %2$s}s → %2${'s}s",
        "sync_save_settings" to "保存设置",
        "sync_saved" to "同步设置已保存",
        "sync_settings_title" to "同步设置",
        "sync_unknown_connection" to "未知连接",
        "sync_upload_changes" to "上传变更",
        "sync_upload_full" to "强制全量上传",
        "terminal_cleaning_old_container" to "正在清理旧容器数据…\n已清理 %1${'s 项}s 项",
        "terminal_close_tab" to "关闭标签",
        "terminal_cursor_bar" to "竖线",
        "terminal_cursor_block" to "方块",
        "terminal_cursor_style_title" to "光标样式",
        "terminal_cursor_underline" to "下划线",
        "terminal_extracting_env" to "正在解压环境…\n已处理 %1${'s 个文件}s 个文件",
        "terminal_font_builtin" to "JetBrains Mono",
        "terminal_font_default" to "系统等宽",
        "terminal_font_delete" to "删除该字体",
        "terminal_font_import" to "导入",
        "terminal_font_import_failed" to "导入失败，请选择有效的 ttf / otf / ttc 字体文件",
        "terminal_font_size_title" to "字体大小",
        "terminal_font_title" to "终端字体",
        "terminal_installing_packages" to "正在安装 python3 / git / pip / node / npm…\n%1${'s}s",
        "terminal_no_open_tabs" to "没有打开的终端标签",
        "terminal_prepare_error_unknown" to "未知错误",
        "terminal_preparing_env_failed" to "正在准备容器环境…\n%1${'s}s",
        "terminal_preparing_env_first_run" to "正在准备容器环境…\n首次运行会解压容器，请稍候",
        "terminal_preview_text" to "终端预览",
        "terminal_retry" to "重试",
        "terminal_settings_title" to "终端设置",
        "terminal_start_failed" to "无法启动终端：\n%1${'s}s",
        "terminal_theme_dracula" to "Dracula",
        "terminal_theme_follow_app" to "跟随应用",
        "terminal_theme_github_dark" to "GitHub Dark",
        "terminal_theme_github_light" to "GitHub Light",
        "terminal_theme_monokai" to "Monokai",
        "terminal_theme_one_dark" to "One Dark",
        "terminal_theme_termius_dark" to "Termius Dark",
        "terminal_theme_title" to "配色主题",
        "terminal_title" to "终端",
        "theme_auto" to "自动",
        "theme_dark" to "深色",
        "theme_dynamic_color" to "动态取色",
        "theme_dynamic_color_desc" to "从系统壁纸提取配色，开启后配色方案不再生效",
        "theme_dynamic_color_unsupported" to "需要 Android 12 及以上系统",
        "theme_light" to "浅色",
        "theme_mode_section" to "明暗模式",
        "theme_preset_coffee" to "咖啡",
        "theme_preset_default" to "默认蓝",
        "theme_preset_forest" to "森林",
        "theme_preset_graphite" to "石墨",
        "theme_preset_section" to "配色方案",
        "title_no_models" to "暂无可用模型。请在「AI 供应商」中添加并配置模型后再选标题总结专用模型。",
        "todo_completed" to "已完成",
        "todo_dashboard_all_done" to "全部完成",
        "todo_dashboard_progress" to "%1${'d/%2$d 完成}d/%2${'d 完成}d 完成",
        "todo_dashboard_title" to "任务待办",
        "todo_in_progress" to "进行中",
        "token_stats_hour_suffix" to "时",
        "tool_browser_condition_met" to "条件已满足。",
        "tool_browser_screenshot_failed" to "暂时无法截图。可稍后重试，或用 getText/getHtml 读取页面内容。",
        "tool_browser_wait_timeout" to "等待超时，条件尚未满足。",
        "tool_changed_files_summary" to "+%1${'d −%2$d · %3$d 个文件}d −%2${'d · %3$d 个文件}d · %3${'d 个文件}d 个文件",
        "tool_command_rule_denied" to "权限规则禁止执行此命令。",
        "tool_command_rule_denied_detail" to "权限规则禁止执行此命令。命中规则：%1${'s；触发片段：%2$s；建议：改用允许的写法，或请用户在「工具授权」中调整规则后重试。}s；触发片段：%2${'s；建议：改用允许的写法，或请用户在「工具授权」中调整规则后重试。}s；建议：改用允许的写法，或请用户在「工具授权」中调整规则后重试。",
        "tool_command_unanalyzable_reason" to "命令含 %1${'s 等无法静态判定的构造，只能单次放行，无法保存为始终允许}s 等无法静态判定的构造，只能单次放行，无法保存为始终允许",
        "tool_expand_remaining" to "展开剩余 %1${'s 行}s 行",
        "tool_instruction" to "指令",
        "tool_mcp_invalid_name" to "MCP 服务器名称只能包含 ASCII 字母、数字、下划线和连字符，当前为：%1${'s。}s。",
        "tool_mcp_stdio_added" to "已添加本地 MCP 服务「%2${'s」（%1$s）。配置将在下一次会话生效；所需运行时需另行安装。}s」（%1${'s）。配置将在下一次会话生效；所需运行时需另行安装。}s）。配置将在下一次会话生效；所需运行时需另行安装。",
        "tool_memory_saved" to "已保存记忆「%1${'s」（%2$s）。后续会话可看到摘要，需要正文时用 read 读取。}s」（%2${'s）。后续会话可看到摘要，需要正文时用 read 读取。}s）。后续会话可看到摘要，需要正文时用 read 读取。",
        "tool_output_omitted" to "[已省略中间 %1${'d 个字符。%2$s]}d 个字符。%2${'s]}s]",
        "tool_output_saved_hint" to "完整内容已保存到 %1${'s，可用 readFile 分段读取。}s，可用 readFile 分段读取。",
        "tool_output_unsaved_hint" to "完整内容保存失败；当前仅保留开头和结尾，可缩小输出范围后重试。",
        "tool_permission_rule_denied" to "权限规则禁止执行此工具。",
        "tool_plan_mode_denied" to "当前为 PLAN（计划）模式，此操作未执行。该模式仅允许只读探索；需要实施时，请提交方案并申请退出 PLAN。",
        "tool_result" to "结果",
        "tool_shizuku_confirmation_reason" to "Shizuku 会以 adb shell 身份操作 Android 系统，需要逐次确认，无法保存为始终允许。",
        "tool_subagent_message_delivered" to "消息已投递。运行中的子代理会尽快收到，已结束的会继续处理消息。",
        "tool_subagent_plan_readonly_warning" to "当前为 PLAN（计划）模式：子代理继承只读权限，文件修改与命令执行会被模式拦截。如确需实施，请先申请退出 PLAN 再派发。",
        "tool_subagent_provider_unresolved" to "该子代理定义里的 provider「%1${'s」未找到（未配置或名称不符），本次将用父会话提供商搭配定义模型「%2$s」，该组合可能无法调用。}s」未找到（未配置或名称不符），本次将用父会话提供商搭配定义模型「%2${'s」，该组合可能无法调用。}s」，该组合可能无法调用。",
        "tool_subagent_tier_fallback" to "请求的「%1${'s」档未配置或对应提供商不可用，已改用「%2$s」档（%3$s）。}s」档未配置或对应提供商不可用，已改用「%2${'s」档（%3$s）。}s」档（%3${'s）。}s）。",
        "tool_subagent_tier_pinned" to "该子代理定义里已写死模型（%1${'s），请求的「%2$s」档被忽略。}s），请求的「%2${'s」档被忽略。}s」档被忽略。",
        "tool_subagent_tier_unavailable" to "请求的「%1${'s」档未配置或对应提供商不可用，该子代理将继承当前会话模型。}s」档未配置或对应提供商不可用，该子代理将继承当前会话模型。",
        "tool_vision_default_multiple" to "请按顺序描述这 %1${'d 张图片中的关键信息，再比较它们的相同点与差异。不清楚的内容请注明。}d 张图片中的关键信息，再比较它们的相同点与差异。不清楚的内容请注明。",
        "tool_vision_default_single" to "请描述图片中的关键信息，包括可辨认的文字、元素和布局。不清楚的内容请注明。",
        "tool_vision_empty_response" to "识图模型未返回分析内容。",
        "tool_vision_focus" to "关注点：%1${'s}s",
        "tool_vision_images_attached" to "已附加 %1${'d 张图片，可直接查看和分析。}d 张图片，可直接查看和分析。",
        "update_channel_latest" to "预览版",
        "update_channel_latest_desc" to "除正式版外，还检测预览版（RC）更新",
        "update_channel_stable" to "稳定版",
        "update_channel_stable_desc" to "仅检测正式版更新",
        "vision_no_models" to "暂无可用模型。请在「AI 供应商」中添加并配置模型后再选识图模型。",
        "web_search_results_count" to "找到 %1${'s 条结果}s 条结果",
        "web_search_title" to "网络搜索",
        "web_search_warning" to "警告：%1${'s}s",
        "workbench_split_handle" to "拖动调整分栏宽度",
        "workspace_add_external" to "添加外部工作区",
        "workspace_confirm" to "确定",
        "workspace_delete" to "删除工作区",
        "workspace_delete_confirm" to "确定删除「%1${'s」？该目录下的所有文件及该工作区的聊天记录都将被删除，且无法恢复。}s」？该目录下的所有文件及该工作区的聊天记录都将被删除，且无法恢复。",
        "workspace_empty_hint" to "暂无工作区，点击「%1${'s」创建一个}s」创建一个",
        "workspace_external_added" to "已添加外部工作区「%1${'s」}s」",
        "workspace_external_exists" to "该目录已作为工作区",
        "workspace_external_invalid" to "所选不是有效目录",
        "workspace_external_remove_confirm" to "移除工作区「%1${'s」？仅解除关联，不会删除该目录及其文件。}s」？仅解除关联，不会删除该目录及其文件。",
        "workspace_external_remove_confirm_sessions" to "移除工作区「%1${'s」？仅解除关联，不会删除该目录及其文件；该工作区的聊天记录将一并删除。}s」？仅解除关联，不会删除该目录及其文件；该工作区的聊天记录将一并删除。",
        "workspace_external_unavailable_hint" to "该目录当前不可用（可能已被移动或删除），可移除该记录",
        "workspace_external_unresolvable" to "无法解析所选目录，请从「内部存储」或「SD 卡」根目录进入选择，暂不支持其它文件提供方",
        "workspace_external_unsupported_path" to "该目录路径包含不支持的字符「:」",
        "workspace_external_unwritable" to "该目录不可写，请在系统设置中授予存储权限后重试",
        "workspace_external_warning_continue" to "继续",
        "workspace_external_warning_dont_show_again" to "不再提示",
        "workspace_external_warning_message" to "AI 会直接读写所选目录中的真实文件。公共存储不支持软链，npm、yarn、pnpm、git 涉及软链的操作可能失败；移除工作区只解除关联，不会删除目录内容。",
        "workspace_external_warning_title" to "添加外部工作区",
        "workspace_name_exists" to "已存在同名工作区",
        "workspace_name_hint" to "例如 my-project",
        "workspace_new_internal" to "新建内部工作区",
        "workspace_new_workspace" to "新建工作区",
        "workspace_open" to "打开工作区",
        "workspace_remote_default_create_failed" to "远程默认工作区创建失败，请检查服务器权限与磁盘空间",
        "workspace_remote_root_create_failed" to "远程工作区目录创建失败，请检查路径与服务器权限",
        "workspace_select" to "选择工作区",
        "workspace_switch" to "切换工作区",
        "workspace_switch_confirm" to "切换工作区会停止当前工作区所有正在运行的 AI 会话并关闭终端标签。确定要切换吗？",
        "workspace_unavailable_current" to "工作区「%1${'s」当前不可用（目录可能已被移动或删除）}s」当前不可用（目录可能已被移动或删除）",
        "workspace_unavailable_fallback" to "工作区「%1${'s」当前不可用，已切换到「%2$s」}s」当前不可用，已切换到「%2${'s」}s」",
    )

    private val en: Map<String, String> = mapOf(
        "about_auto_check_update" to "Auto check for updates",
        "about_auto_check_update_desc" to "Check for new versions when the app opens",
        "about_check_failed" to "Check failed",
        "about_check_update" to "Check for updates",
        "about_checking_update" to "Querying GitHub for the latest version…",
        "about_description" to "Universal AI Coding Agent for Android · Linux dev environment · Local & remote SSH",
        "about_download" to "Download",
        "about_github_repo" to "GitHub Repository",
        "about_later" to "Later",
        "about_license" to "Open Source License",
        "about_network_error" to "Network error",
        "about_new_version_found" to "New version available",
        "about_no_changelog" to "No changelog available",
        "about_ok" to "OK",
        "about_parse_version_failed" to "Cannot parse the latest version number",
        "about_up_to_date" to "Already up to date",
        "about_up_to_date_detail" to "Current version v%1${'s, already the latest Release on GitHub.}s, already the latest Release on GitHub.",
        "about_update_apk_ready" to "Update package is ready — tap Install to finish",
        "about_update_background" to "Continue in background",
        "about_update_browser" to "Download in browser",
        "about_update_channel" to "Update Channel",
        "about_update_download_failed" to "Download failed",
        "about_update_download_failed_hint" to "Change the mirror in Settings → About → Download mirror and retry",
        "about_update_download_install" to "Download & install",
        "about_update_download_size" to "%1${'s / %2$s}s / %2${'s}s",
        "about_update_downloading" to "Downloading %1${'d%%}d%%",
        "about_update_install" to "Install",
        "about_update_install_failed" to "No app available to install packages",
        "about_update_mirror" to "Download mirror",
        "about_update_mirror_default" to "Restore default",
        "about_update_mirror_desc" to "Speeds up update downloads; leave empty to use GitHub directly",
        "about_update_mirror_invalid" to "Address must start with http:// or https://",
        "about_update_mirror_preset_cdn" to "gh-proxy CDN (default)",
        "about_update_mirror_preset_direct" to "GitHub direct",
        "about_update_mirror_preset_v6" to "gh-proxy preferred node",
        "about_update_need_permission" to "Allow “install unknown apps” first, then come back and tap Install",
        "about_update_retry" to "Retry",
        "about_update_verify_not_apk" to "The downloaded file is not a valid package",
        "about_update_verify_not_newer" to "Package version is not newer than the installed one",
        "about_update_verify_package" to "The package does not match this app",
        "about_update_verify_signature" to "Package signature differs from this app — download in browser instead",
        "about_version" to "Version",
        "agent_compaction_failed" to "Compaction failed: %1${'s}s",
        "agent_complete_notification_background_body" to "Background task \"%1${'s\" finished}s\" finished",
        "agent_complete_notification_background_multi_body" to "%1${'d background tasks finished}d background tasks finished",
        "agent_complete_notification_body" to "Tap to view",
        "agent_complete_notification_title" to "Task complete",
        "agent_context_no_compaction" to "No compaction needed (too few messages)",
        "agent_input_budget_exceeded" to "Context exceeds the model input budget. Shorten the input or reduce tool definitions.",
        "agent_model_not_selected" to "Not selected",
        "agent_stop_context_exceeded" to "This request exceeds the model's context window. Compact the context or start a new session.",
        "agent_stop_refusal" to "The model declined to answer under its content safety policy.",
        "agent_stopped_by_user" to "Stopped by user",
        "agent_tool_executing" to "%1${'s running…}s running…",
        "agreement_accept" to "Agree and continue",
        "agreement_decline" to "Disagree",
        "agreement_notice_conjunction" to "\" and \"",
        "agreement_notice_prefix" to "\"Before using AiCode, please read and agree to the \"",
        "agreement_notice_suffix" to "\" to understand the usage rules and data handling practices.\nBy tapping “Agree and continue”, you confirm that you have read and agree to both documents.\"",
        "agreement_privacy" to "Privacy Policy",
        "agreement_privacy_link" to "Privacy Policy",
        "agreement_privacy_url" to "https://aicode.murk.top/en/privacy",
        "agreement_terms" to "User Agreement",
        "agreement_terms_link" to "User Agreement",
        "agreement_terms_url" to "https://aicode.murk.top/en/terms",
        "agreement_title" to "Welcome to AiCode",
        "app_name" to "AiCode",
        "ask_confirm" to "Confirm",
        "ask_confirm_title" to "AI needs your confirmation",
        "ask_custom_answer" to "Enter a custom answer",
        "ask_input_hint" to "Please enter…",
        "ask_supplement" to "Supplement",
        "backup_actions" to "Actions",
        "backup_data_app_settings" to "App Settings",
        "backup_data_chat_history" to "Chat History",
        "backup_data_mcp" to "MCP Servers",
        "backup_data_permissions" to "Permission Rules",
        "backup_data_remote" to "Connections & Sync",
        "backup_data_workspace" to "Workspace Files",
        "backup_export_btn" to "Export",
        "backup_export_failed" to "Export failed",
        "backup_export_subtitle" to "Select data to export, optionally password-encrypted",
        "backup_export_title" to "Export Backup",
        "backup_exported" to "Backup exported",
        "backup_import_btn" to "Import",
        "backup_import_done" to "Import complete",
        "backup_import_failed" to "Import failed",
        "backup_import_subtitle" to "Restore data from backup file",
        "backup_import_title" to "Import Backup",
        "backup_net_access_key" to "Access Key",
        "backup_net_add_target" to "Add backup target",
        "backup_net_base_path" to "Remote directory",
        "backup_net_base_path_hint" to "WebDAV directory or S3 object prefix",
        "backup_net_bucket" to "Bucket",
        "backup_net_delete_failed" to "Failed to delete cloud backup",
        "backup_net_delete_target_confirm" to "Delete backup target %1${'s?}s?",
        "backup_net_edit_target" to "Edit backup target",
        "backup_net_endpoint" to "Server URL",
        "backup_net_endpoint_hint" to "e.g. https://dav.example.com/dav",
        "backup_net_list_failed" to "Failed to read cloud backups",
        "backup_net_loading" to "Loading cloud backups…",
        "backup_net_name" to "Name",
        "backup_net_no_target" to "No cloud backup target yet",
        "backup_net_no_target_hint" to "Tap + in the top right to add a WebDAV or S3 backup target",
        "backup_net_password" to "Password",
        "backup_net_path_style" to "Path-style addressing",
        "backup_net_region" to "Region",
        "backup_net_remote_delete" to "Delete",
        "backup_net_remote_delete_confirm" to "Delete cloud backup %1${'s? This cannot be undone.}s? This cannot be undone.",
        "backup_net_remote_empty" to "No backups in the cloud",
        "backup_net_remote_list_title" to "Cloud backups",
        "backup_net_remote_restore" to "Restore",
        "backup_net_restore" to "Restore from cloud",
        "backup_net_restore_subtitle" to "Download a backup from the selected target and restore",
        "backup_net_secret_key" to "Secret Key",
        "backup_net_section" to "Cloud backup",
        "backup_net_session_token" to "Session Token (optional)",
        "backup_net_targets" to "Backup targets",
        "backup_net_test_connection" to "Test connection",
        "backup_net_test_failed" to "Connection failed",
        "backup_net_test_success" to "Connection succeeded",
        "backup_net_testing" to "Testing connection…",
        "backup_net_type_s3" to "Object storage (S3)",
        "backup_net_type_webdav" to "WebDAV",
        "backup_net_upload" to "Upload",
        "backup_net_upload_done" to "Uploaded to %1${'s}s",
        "backup_net_upload_failed" to "Upload failed",
        "backup_net_upload_password_hint" to "The backup will be uploaded to the cloud. A password is required (AES-GCM) to keep API keys, SSH credentials, etc. from being exposed in plain text",
        "backup_net_upload_password_required" to "An encryption password is required for cloud backup",
        "backup_net_upload_short" to "Upload",
        "backup_net_upload_subtitle" to "Export the current backup and upload to the selected target",
        "backup_net_uploading" to "Uploading backup…",
        "backup_net_username" to "Username",
        "backup_operation_failed" to "Operation failed",
        "backup_password_hint" to "Leave empty for unencrypted tar.gz; enter a password for AES-GCM encryption",
        "backup_password_input" to "Enter backup password",
        "backup_password_label" to "Password (optional)",
        "backup_password_optional_hint" to "Leave empty if backup is unencrypted",
        "backup_processing" to "Processing",
        "backup_processing_data" to "Processing data…",
        "backup_read_failed" to "Failed to read backup file",
        "backup_restore_workspaces_confirm" to "Restore",
        "backup_restore_workspaces_files" to "%1${'d files}d files",
        "backup_restore_workspaces_select_all" to "Select all",
        "backup_restore_workspaces_title" to "Restore workspaces",
        "backup_restore_workspaces_warning" to "Files in local workspaces with the same name will be overwritten and cannot be undone. Select carefully.",
        "backup_restored_data" to "Restored data:",
        "backup_select_data" to "Select export data",
        "backup_set_password" to "Set export password",
        "backup_settings_covered" to "Theme, keepalive, logging, vision, and sync preferences covered",
        "backup_stat_chat_messages" to "· Chat Messages: %1${'s}s",
        "backup_stat_chat_sessions" to "· Chat Sessions: %1${'s}s",
        "backup_stat_mcp_servers" to "· MCP Servers: %1${'s}s",
        "backup_stat_permission_rules" to "· Global Permission Rules: %1${'s}s",
        "backup_stat_providers" to "· AI Vendors: %1${'s}s",
        "backup_stat_remote_connections" to "· Remote Connections: %1${'s}s",
        "backup_stat_remote_mounts" to "· Remote Mounts: %1${'s}s",
        "backup_stat_todo_items" to "· Todo Items: %1${'s}s",
        "backup_stat_workspace_files" to "· Workspace Files: %1${'s}s",
        "backup_tab_local" to "Local",
        "backup_workspace_git_hint" to "Workspace files are best backed up via Git; the export here is only a fallback",
        "backup_write_failed" to "Write failed: %1${'s}s",
        "backup_wrong_password" to "Wrong backup password, or the encrypted backup file is corrupted",
        "browser_address_hint" to "Enter URL or search",
        "browser_back" to "Back",
        "browser_close_tab" to "Close tab",
        "browser_devtools" to "Developer tools",
        "browser_devtools_desc" to "Enable mobile Eruda debug console",
        "browser_devtools_title" to "Developer tools",
        "browser_dialog_alert_title" to "Page Alert",
        "browser_dialog_confirm_title" to "Page Confirmation",
        "browser_dialog_prompt_title" to "Page Input",
        "browser_empty_click_hint" to "Tap above to enter a URL and start browsing",
        "browser_empty_hint" to "Enter a URL in the address bar above to start browsing",
        "browser_forward" to "Forward",
        "browser_menu" to "More options",
        "browser_menu_general_section" to "Display & Tools",
        "browser_menu_title" to "Browser Options",
        "browser_new_tab" to "New tab",
        "browser_night_mode_desc" to "Dim page brightness and enable dark mode preference",
        "browser_night_mode_off" to "Turn on night mode",
        "browser_night_mode_on" to "Turn off night mode",
        "browser_night_mode_title" to "Night mode",
        "browser_not_ready" to "Browser not ready. Enter a URL in the address bar to start browsing.",
        "browser_reload" to "Reload",
        "browser_tab_default_title" to "New Tab",
        "browser_tabs_count" to "Tabs (%1${'d)}d)",
        "browser_tabs_manage" to "Manage Tabs",
        "browser_title" to "Browser",
        "browser_ua_android_phone" to "Android Phone",
        "browser_ua_android_phone_desc" to "Chrome Mobile (Android)",
        "browser_ua_android_tablet" to "Android Tablet",
        "browser_ua_android_tablet_desc" to "Chrome Tablet (Android)",
        "browser_ua_applied_toast" to "User-Agent updated, page reloaded",
        "browser_ua_custom" to "Custom",
        "browser_ua_custom_desc" to "Specify custom User-Agent string",
        "browser_ua_custom_dialog_title" to "Custom User-Agent",
        "browser_ua_custom_input_hint" to "Enter full User-Agent string",
        "browser_ua_default" to "System default",
        "browser_ua_default_desc" to "Native WebView User-Agent",
        "browser_ua_desc" to "Emulate different device environments",
        "browser_ua_desktop_pc" to "Desktop PC",
        "browser_ua_desktop_pc_desc" to "Chrome Desktop (Windows PC)",
        "browser_ua_ipad" to "iPad",
        "browser_ua_ipad_desc" to "Tablet Safari (iPadOS)",
        "browser_ua_iphone" to "iPhone",
        "browser_ua_iphone_desc" to "Mobile Safari (iOS)",
        "browser_ua_section" to "User-Agent",
        "chat_action_copy" to "Copy Text",
        "chat_action_delete" to "Delete Message",
        "chat_action_edit" to "Edit Message",
        "chat_action_fork" to "Fork From Here",
        "chat_add_attachment" to "Add attachment",
        "chat_approve_and_implement" to "Approve and implement",
        "chat_attachment_max" to "Up to %1${'s attachments at once}s attachments at once",
        "chat_attachment_open" to "Open %1${'s}s",
        "chat_attachment_prefix" to "Attachments:",
        "chat_attachment_preview" to "Preview %1${'s}s",
        "chat_attachment_uploaded" to "%1${'s attachments uploaded}s attachments uploaded",
        "chat_attachment_uploaded_partial" to "%1${'s attachments uploaded, some were not added}s attachments uploaded, some were not added",
        "chat_attachment_uploading" to "Uploading %1${'d files…}d files…",
        "chat_batch_delete_confirm" to "Delete the selected %1${'d sessions? All messages in these sessions will be cleared and cannot be undone.}d sessions? All messages in these sessions will be cleared and cannot be undone.",
        "chat_batch_delete_title" to "Delete Selected Sessions",
        "chat_batch_select" to "Select Multiple",
        "chat_bg_command_done" to "Background command completed",
        "chat_bg_commands_done" to "%1${'d background tasks completed}d background tasks completed",
        "chat_bg_commands_partial_failed" to "%1${'d background tasks finished, %2$d failed}d background tasks finished, %2${'d failed}d failed",
        "chat_cache_hit_rate" to "Cache hit rate %1${'s}s",
        "chat_camera_file_failed" to "Cannot create camera file",
        "chat_compaction_failed" to "Context compaction failed",
        "chat_compressing_context" to "Compressing context",
        "chat_connecting_remote" to "Connecting to remote server…",
        "chat_context_compressed" to "Context compressed",
        "chat_context_usage" to "Context %1${'d%% used}d%% used",
        "chat_context_usage_estimated" to "Estimated input budget usage: %1${'d%%}d%%",
        "chat_continue_feedback" to "Continue feedback",
        "chat_copied" to "Copied",
        "chat_copy" to "Copy",
        "chat_delete_session" to "Delete Session",
        "chat_delete_session_confirm" to "Delete \"%1${'s\"? All messages in this session will be cleared and cannot be undone.}s\"? All messages in this session will be cleared and cannot be undone.",
        "chat_edit_dialog_hint" to "Enter message content",
        "chat_edit_dialog_title" to "Edit Message",
        "chat_error_title" to "Request failed",
        "chat_export_session" to "Export Session",
        "chat_export_session_done" to "Session exported",
        "chat_export_session_failed" to "Export failed",
        "chat_fork_title" to "Fork of %1${'s}s",
        "chat_goal_clear" to "Clear goal",
        "chat_goal_label" to "Goal · %1${'s}s",
        "chat_goal_pause" to "Pause goal",
        "chat_goal_resume" to "Resume goal",
        "chat_goal_status_active" to "Active",
        "chat_goal_status_blocked" to "Blocked",
        "chat_goal_status_completed" to "Completed",
        "chat_goal_status_exhausted" to "Budget exhausted",
        "chat_goal_status_paused" to "Paused",
        "chat_history" to "History",
        "chat_image_too_large" to "Image exceeds %1${'s, please compress and retry}s, please compress and retry",
        "chat_input_expand" to "Expand input box",
        "chat_input_fullscreen_title" to "Full-screen input",
        "chat_input_hint" to "Describe what you need, and I'll help you write, modify, or explain code",
        "chat_input_placeholder" to "Type a message to chat with AI…",
        "chat_key_switched" to "Switched to key %1${'d of %2$d}d of %2${'d}d",
        "chat_mention_empty" to "No matching files",
        "chat_mention_error" to "Failed to load file list",
        "chat_mention_loading" to "Loading files…",
        "chat_mention_unavailable" to "Container not ready, cannot search files",
        "chat_more_options" to "More Options",
        "chat_new_session" to "New Session",
        "chat_new_session_btn" to "New Chat",
        "chat_no_model_selected" to "No model selected",
        "chat_no_models_hint" to "No models available, add one in \"AI Vendors\"",
        "chat_no_remote_connection" to "Not connected to remote server",
        "chat_no_sessions_hint" to "No sessions yet",
        "chat_open_apk_permission" to "Installing apps requires permission. Allow installs from this source in the settings that just opened",
        "chat_open_browser" to "Open browser",
        "chat_open_file_missing" to "File not found or has been moved",
        "chat_open_file_no_app" to "No app available to open this file",
        "chat_open_file_unshareable" to "This file's location cannot be shared with other apps",
        "chat_open_git" to "Open Git",
        "chat_open_sidebar" to "Open sidebar",
        "chat_open_terminal" to "Open terminal",
        "chat_perm_always_allow" to "Always Allow",
        "chat_perm_always_prefix" to "\"Always allow\" will remember for this project:",
        "chat_perm_always_tool_desc" to "\"Always allow\" will remember all calls of this tool for this project",
        "chat_perm_deny" to "Deny",
        "chat_perm_session_label" to "Session \"%1${'s\"}s\"",
        "chat_perm_single_use_desc" to "Contains command substitution/pipes/redirects, cannot be remembered, single-use only",
        "chat_pin_session" to "Pin to Top",
        "chat_placeholder" to "How can I help you?",
        "chat_plan_completed" to "Plan completed",
        "chat_queue_drag" to "Drag to reorder",
        "chat_queue_edit" to "Edit queued message",
        "chat_queue_hint" to "AI is busy, press enter to queue the message",
        "chat_queue_interject" to "Insert to AI now",
        "chat_queue_remove" to "Remove from queue",
        "chat_queue_title" to "Queue (%1${'d)}d)",
        "chat_read_file_failed" to "Cannot read the selected file",
        "chat_reasoning_cannot_disable" to "This model always thinks; the lowest level is used instead",
        "chat_reasoning_default_tag" to "Default",
        "chat_reasoning_effective" to "Effective: %1${'s}s",
        "chat_reasoning_effective_tag" to "Active",
        "chat_reasoning_effort" to "Reasoning effort",
        "chat_reasoning_effort_high" to "High",
        "chat_reasoning_effort_low" to "Low",
        "chat_reasoning_effort_max" to "Max",
        "chat_reasoning_effort_medium" to "Medium",
        "chat_reasoning_effort_minimal" to "Minimal",
        "chat_reasoning_effort_none" to "None",
        "chat_reasoning_effort_xhigh" to "Extra high",
        "chat_reasoning_endpoint_weak" to "This endpoint may ignore \"None\"; switch to the provider's Responses endpoint to fully disable thinking",
        "chat_reasoning_off_short" to "Off",
        "chat_reasoning_toggle" to "Thinking",
        "chat_reasoning_unknown_hint" to "Levels are unknown for this model, so the selected level may not take effect",
        "chat_remote_connect_failed" to "Remote server connection failed, please check settings",
        "chat_remove_attachment" to "Remove attachment",
        "chat_rename_session" to "Rename Session",
        "chat_retrying" to "Retrying %1${'s/%2$s}s/%2${'s}s",
        "chat_search_clear" to "Clear",
        "chat_search_hint" to "Search chats",
        "chat_search_no_result" to "No matching messages",
        "chat_select_workspace_first" to "Please select a workspace first",
        "chat_send" to "Send",
        "chat_session_name" to "Session name",
        "chat_sessions_selected_count" to "%1${'d sessions selected}d sessions selected",
        "chat_settings" to "Settings",
        "chat_ssh_connected" to "SSH: Connected",
        "chat_ssh_connecting" to "SSH: Connecting…",
        "chat_ssh_disconnected" to "SSH: Not connected",
        "chat_ssh_failed" to "SSH: Connection failed",
        "chat_status_asking_user" to "Asking user",
        "chat_status_browsing_web" to "Controlling browser",
        "chat_status_calling_tool" to "Calling tool",
        "chat_status_editing_file" to "Editing file",
        "chat_status_generating" to "Generating",
        "chat_status_generating_image" to "Generating image",
        "chat_status_reading_file" to "Reading file",
        "chat_status_reading_memory" to "Reading memory",
        "chat_status_running_command" to "Running command",
        "chat_status_searching_web" to "Searching the web",
        "chat_status_starting_subagent" to "Starting sub-agent",
        "chat_status_thinking" to "Thinking",
        "chat_status_updating_todo" to "Updating to-dos",
        "chat_status_viewing_image" to "Viewing image",
        "chat_stop" to "Stop",
        "chat_take_photo" to "Take photo",
        "chat_task_duration" to "Total time %1${'s}s",
        "chat_thinking_done" to "Thought",
        "chat_thinking_done_time" to "Thought for %1${'ss}ss",
        "chat_thinking_process" to "Thinking process",
        "chat_thinking_running_time" to "Thinking (%1${'ss)}ss)",
        "chat_tool_calls_count" to "%1${'d tool calls}d tool calls",
        "chat_tool_failed" to "Tool failed",
        "chat_tool_running" to "Tool running",
        "chat_tool_succeeded" to "Tool succeeded",
        "chat_turn_completed" to "Completed in %1${'s}s",
        "chat_turn_completed_plain" to "Completed",
        "chat_turn_running" to "Running",
        "chat_unpin_session" to "Unpin",
        "chat_unsupported_image_type" to "Unrecognized image type, only jpg/png/gif/webp supported",
        "chat_upload_failed" to "Upload failed",
        "chat_upload_file" to "Upload File",
        "chat_upload_image" to "Upload Image",
        "chat_uploaded_to" to "Uploaded to %1${'s}s",
        "chat_workspace_unavailable" to "Workspace unavailable",
        "checkpoint_restore_code" to "Restore code only",
        "checkpoint_restore_code_and_conversation" to "Restore code and conversation",
        "checkpoint_restore_code_and_conversation_desc" to "Revert both code and conversation before this checkpoint",
        "checkpoint_restore_code_desc" to "Revert code to snapshot before this point, keep conversation",
        "checkpoint_restore_conversation" to "Restore conversation only",
        "checkpoint_restore_conversation_desc" to "Keep current code, truncate conversation to this point",
        "checkpoint_rewind_title" to "Checkpoint & Rewind",
        "checkpoint_skipped_multiple" to "Skipped %1${'d files: they changed after the snapshot, so they were left alone to keep those edits\n%2$s}d files: they changed after the snapshot, so they were left alone to keep those edits\n%2${'s}s",
        "checkpoint_skipped_single" to "Skipped 1 file: it changed after the snapshot, so it was left alone to keep those edits\n%1${'s}s",
        "checkpoint_target_prompt" to "Target message: %1${'s}s",
        "common_add" to "Add",
        "common_ai_providers" to "AI Vendors",
        "common_allow" to "Allow",
        "common_back" to "Back",
        "common_batch_delete" to "Delete",
        "common_batch_deleting" to "Deleting…",
        "common_batch_disable" to "Disable",
        "common_batch_enable" to "Enable",
        "common_batch_reconnect" to "Reconnect",
        "common_cancel" to "Cancel",
        "common_clear" to "Clear",
        "common_close" to "Close",
        "common_collapse" to "Collapse",
        "common_collapse_action" to "Collapse",
        "common_confirm" to "Confirm",
        "common_copied_with_title" to "Copied: %1${'s}s",
        "common_copy" to "Copy",
        "common_copy_success" to "Copied to clipboard",
        "common_create" to "Create",
        "common_cut" to "Cut",
        "common_delete" to "Delete",
        "common_deselect_all" to "Deselect All",
        "common_disabled" to "Disabled",
        "common_done" to "Done",
        "common_edit" to "Edit",
        "common_enabled" to "Enabled",
        "common_error" to "Error",
        "common_error_unknown" to "Unknown error",
        "common_expand" to "Expand",
        "common_file" to "File",
        "common_go_to_settings" to "Go to Settings",
        "common_got_it" to "Got it",
        "common_hide" to "Hide",
        "common_image_load_failed" to "Failed to load image; the file may have been moved or deleted",
        "common_image_preview" to "Image Preview",
        "common_label_with_api" to "%1${'s (%2$s)}s (%2${'s)}s)",
        "common_local" to "Local",
        "common_model" to "Model",
        "common_more" to "More",
        "common_name" to "Name",
        "common_new_tab" to "New Tab",
        "common_next" to "Next",
        "common_open_link_failed" to "Cannot open link",
        "common_other" to "Other",
        "common_overwrite" to "Overwrite",
        "common_paste" to "Paste",
        "common_prev" to "Previous",
        "common_rename" to "Rename",
        "common_reset" to "Reset",
        "common_retry" to "Retry",
        "common_save" to "Save",
        "common_scroll_to_bottom" to "Back to bottom",
        "common_select" to "Select",
        "common_select_all" to "Select All",
        "common_select_mode" to "Select",
        "common_selected_count" to "%1${'d selected}d selected",
        "common_show" to "Show",
        "common_switch" to "Switch",
        "common_tool" to "Tool",
        "common_tool_missing_argument" to "Missing parameter: %1${'s.}s.",
        "common_username" to "Username",
        "common_workspace" to "Workspace",
        "compaction_no_models" to "No models available. Please add and configure a model in \"AI Vendors\" before selecting a dedicated compaction model.",
        "container_add_arg" to "Add arg",
        "container_add_binding" to "Add mount",
        "container_add_env_var" to "Add environment variable",
        "container_add_image" to "Add Image",
        "container_announcement_got_it" to "Got it",
        "container_announcement_title" to "FAQ",
        "container_arg_value" to "Arg value",
        "container_builtin_badge" to "Built-in",
        "container_builtin_restorable" to "Built-in images can be restored from the empty list.",
        "container_channel_deleted" to "Channel deleted",
        "container_custom_badge" to "Custom",
        "container_custom_image" to "Custom Image",
        "container_default_badge" to "Default",
        "container_default_desc" to "In remote workspace mode, local MCP services run in this container",
        "container_delete_config" to "Delete Image Config",
        "container_delete_confirm" to "Delete \"%1${'s\"? %2$s}s\"? %2${'s}s",
        "container_download_architecture" to "Architecture: %1${'s}s",
        "container_download_batch_delete_confirm" to "Delete the selected %1${'d image files? Imported containers will no longer be able to unpack them.}d image files? Imported containers will no longer be able to unpack them.",
        "container_download_batch_delete_title" to "Delete Downloaded Images",
        "container_download_cancel" to "Cancel download",
        "container_download_checksum_failed" to "Checksum mismatch — the file may be truncated or tampered with. Please retry.",
        "container_download_current_source" to "Source: %1${'s}s",
        "container_download_current_source_title" to "Choose download source",
        "container_download_delete_confirm" to "Delete the downloaded file for \"%1${'s\"? Imported containers will not be able to extract again.}s\"? Imported containers will not be able to extract again.",
        "container_download_delete_title" to "Delete downloaded image",
        "container_download_done" to "Downloaded",
        "container_download_eol_ended" to "No longer supported",
        "container_download_eol_extended" to "In %1${'s extended support}s extended support",
        "container_download_eol_extended_generic" to "In extended support",
        "container_download_eol_fully_ends" to "Fully end-of-life: %1${'s}s",
        "container_download_eol_soon" to "Support ending soon",
        "container_download_eol_support_ends" to "Standard support ended: %1${'s}s",
        "container_download_failed" to "Download failed: %1${'s}s",
        "container_download_image" to "Download Images",
        "container_download_import" to "Import",
        "container_download_imported_toast" to "Imported to the container list; it will be extracted on first use in Terminal",
        "container_download_list_empty" to "No images available",
        "container_download_newer_available" to "Update available",
        "container_download_redownload" to "Redownload",
        "container_download_size" to "Size: %1${'s}s",
        "container_download_source_unavailable" to "Not available on this mirror",
        "container_download_start" to "Download",
        "container_edit_image" to "Edit Image",
        "container_empty" to "No containers or images",
        "container_empty_desc" to "Add a custom image, or restore the built-in Alpine.",
        "container_env_name" to "Variable name",
        "container_env_value" to "Variable value",
        "container_env_vars" to "Environment variables",
        "container_extra_bindings" to "Mounts",
        "container_extra_proot_args" to "Proot args",
        "container_file_selected" to "File selected",
        "container_importing" to "Copying image file…",
        "container_local_image" to "Local Image",
        "container_mount_container" to "Container path",
        "container_mount_local" to "Local path",
        "container_no_args" to "No args",
        "container_no_bindings" to "No mounts",
        "container_no_env_vars" to "No custom environment variables",
        "container_no_sftp_channel" to "No SFTP channel available. Please configure an SFTP connection in the \"Connections & Sync\" page first.",
        "container_not_ready_hint" to "Container is not initialized yet. Please open the Terminal page to complete initialization first.",
        "container_remote_ssh" to "Remote SSH",
        "container_remote_workspace_path" to "Remote Workspace Path",
        "container_reset" to "Reset",
        "container_reset_confirm" to "This will delete the rootfs directory of \"%1${'s\" (including installed tools and configs). It will be re-extracted and initialized on next use. Continue?}s\" (including installed tools and configs). It will be re-extracted and initialized on next use. Continue?",
        "container_reset_title" to "Reset Container",
        "container_resetting_message" to "Deleting data for \"%1${'s\"…\n%2$s items removed}s\"…\n%2${'s items removed}s items removed",
        "container_resetting_title" to "Cleaning Container Data",
        "container_restore_builtin" to "Restore Built-in Alpine",
        "container_rootfs_will_be_cleared" to "Its rootfs directory will also be removed.",
        "container_select_image_file" to "Select tar.gz / tgz / tar.xz / txz image file",
        "container_select_ssh_channel" to "Select SSH Channel",
        "container_set_as_default" to "Set as default container",
        "container_shell_desc" to "Shell: %1${'s}s",
        "container_shell_path" to "Shell",
        "container_sort_long_press" to "Long press to reorder",
        "container_ssh_badge" to "SSH",
        "container_ssh_channel" to "SSH Channel",
        "container_switch_confirm" to "After switching to \"%1${'s\", the currently running AI sessions will be stopped and terminal tabs will be closed. Continue?}s\", the currently running AI sessions will be stopped and terminal tabs will be closed. Continue?",
        "container_switch_image" to "Switch Container & Images",
        "crash_banner_title" to "Application Error",
        "crash_copied" to "Error details copied",
        "crash_copy" to "Copy error details",
        "crash_exit" to "Exit app",
        "crash_field_device" to "Device",
        "crash_field_memory" to "Memory Usage",
        "crash_field_screen" to "Screen",
        "crash_field_thread" to "Thread",
        "crash_field_time" to "Time",
        "crash_field_version" to "App Version",
        "crash_field_workspace_mode" to "Workspace Mode",
        "crash_log_hint" to "Full log files at: %1${'s}s",
        "crash_message" to "Something went wrong and the error has been recorded. Please copy the details below and send them to the developer to help us improve the app.",
        "crash_mode_local" to "Local PRoot Container",
        "crash_mode_remote" to "Remote SSH",
        "crash_mode_unknown" to "Unknown",
        "crash_report_device" to "Device: %1${'s}s",
        "crash_report_log" to "Log files: %1${'s}s",
        "crash_report_memory" to "Memory: %1${'s}s",
        "crash_report_mode" to "Workspace Mode: %1${'s}s",
        "crash_report_screen" to "Screen: %1${'s}s",
        "crash_report_thread" to "Thread: %1${'s}s",
        "crash_report_time" to "Time: %1${'s}s",
        "crash_report_title" to "AiCode Crash Report",
        "crash_report_version" to "Version: %1${'s}s",
        "crash_restart" to "Restart app",
        "crash_screen_chat" to "Chat",
        "crash_screen_git" to "Git",
        "crash_screen_settings" to "Settings",
        "crash_screen_terminal" to "Terminal",
        "crash_screen_unknown" to "Unknown",
        "crash_section_environment" to "Environment & Runtime",
        "crash_section_stack" to "Exception Stack Trace",
        "crash_title" to "App encountered an unexpected error",
        "credential_add" to "Add Credential",
        "credential_add_github_desc" to "One-tap sign-in with GitHub account, no manual token required",
        "credential_add_github_title" to "GitHub Quick Sign-in",
        "credential_add_manual" to "Add manually",
        "credential_add_manual_desc" to "Fill in remote host, username and access token",
        "credential_add_manual_subtitle" to "For GitLab, Gitee, self-hosted servers or personal access tokens",
        "credential_add_manual_title" to "Configure Manually",
        "credential_add_recommend" to "Recommended",
        "credential_add_sheet_subtitle" to "Choose quick authorization or configure access credentials manually",
        "credential_add_sheet_title" to "Add Credential",
        "credential_auto_inject_enable" to "Enable",
        "credential_auto_inject_hint" to "Sync git credentials saved in the app to this remote server, effective only for repositories under the current workspace root. Does not affect your manual git usage on the server.",
        "credential_auto_inject_prompt_body" to "Allow AiCode to sync git credentials saved in the app to this remote server? It only applies to repositories under the current workspace root and will not modify the server's global git config.",
        "credential_auto_inject_prompt_title" to "Auto-inject git credentials?",
        "credential_auto_inject_title" to "Auto-inject credentials",
        "credential_device_authorize_and_open" to "Copy & Authorize",
        "credential_device_bad_client" to "Invalid client ID, please check the app configuration",
        "credential_device_card_label" to "Verification Code",
        "credential_device_code_copied" to "Device code copied",
        "credential_device_code_copied_opening" to "Device code copied, opening browser…",
        "credential_device_copy_code" to "Copy code only",
        "credential_device_denied" to "Authorization was denied",
        "credential_device_expired" to "Device code expired, please retry",
        "credential_device_failed" to "Sign-in failed: %1${'s}s",
        "credential_device_failed_title" to "Authorization Incomplete",
        "credential_device_flow_disabled" to "Device flow is not enabled for this app, please enable it in the GitHub App settings",
        "credential_device_install_later" to "Later",
        "credential_device_install_now" to "Install",
        "credential_device_install_required" to "Signed in as %1${'s. You still need to install AiCode on your repositories before pushing/pulling code.}s. You still need to install AiCode on your repositories before pushing/pulling code.",
        "credential_device_instruction" to "Open the address below in a browser and enter the device code to authorize",
        "credential_device_open_browser" to "Open browser",
        "credential_device_requesting" to "Requesting device code…",
        "credential_device_step_1" to "1. Copy the 8-character verification code above",
        "credential_device_step_2" to "2. Paste and confirm authorization on the GitHub page",
        "credential_device_subtitle" to "Securely authorize access permissions via GitHub device flow",
        "credential_device_success" to "Signed in as %1${'s and credential saved}s and credential saved",
        "credential_device_success_title" to "Authorization Successful",
        "credential_device_tap_to_copy" to "Tap to copy",
        "credential_device_title" to "GitHub Sign-in",
        "credential_device_unknown_error" to "Unknown error",
        "credential_device_waiting" to "Waiting for browser authorization…",
        "credential_device_waiting_desc" to "Credentials will be bound and saved automatically once authorized",
        "credential_edit" to "Edit Credential",
        "credential_empty_desc" to "Tap + in the top right to sign in with GitHub or add an access token. AiCode then authenticates pushes and pulls automatically",
        "credential_empty_hint" to "No credentials yet, tap + to add",
        "credential_empty_title" to "No Git Credentials",
        "credential_host" to "Remote Host",
        "credential_prompt_desc" to "Once filled, it will be saved and retried automatically. The credential is also saved to git inside the container, so terminal and AI can use it directly for bare git commands.",
        "credential_prompt_title" to "Login credentials needed for %1${'s}s",
        "credential_quick_login" to "Quick login",
        "credential_quick_login_desc" to "Sign in via GitHub device code",
        "credential_quick_login_github_desc" to "Sign in with your GitHub account",
        "credential_quick_login_title" to "Quick login",
        "credential_remote_host" to "Remote Host",
        "credential_save_and_retry" to "Save and Retry",
        "credential_toast_deleted" to "Credential deleted",
        "credential_toast_identity_saved" to "Identity and repository URL saved",
        "credential_toast_save_failed" to "Save failed: %1${'s}s",
        "credential_toast_saved" to "Credential saved",
        "credential_token" to "Access Token / PAT",
        "dashboard_exec_failed" to "Execution failed",
        "dashboard_fetch_failed_retry" to "Load failed, tap to retry",
        "dashboard_fetching" to "Loading dashboard…",
        "dashboard_query_failed" to "Query failed",
        "dashboard_tap_to_query" to "Tap to refresh",
        "diff_binary_file" to "Binary file, text diff not supported",
        "diff_file_too_large" to "File too large, diff calculation skipped",
        "diff_file_too_large_desc" to "Files over 2000 lines or with extremely long lines are not diffed line-by-line, to avoid rendering and memory pressure.",
        "diff_no_changes" to "No differences",
        "drawer_collapse_subagents" to "Collapse subagents",
        "drawer_expand_subagents" to "Expand subagents",
        "drawer_tab_files" to "Files",
        "editor_binary_unsupported" to "Binary file, opening not supported",
        "editor_cursor_left" to "Move cursor left",
        "editor_cursor_right" to "Move cursor right",
        "editor_discard" to "Discard",
        "editor_dont_save" to "Don't save",
        "editor_file_too_large" to "File too large (%1${'d MB) to open}d MB) to open",
        "editor_font_size" to "Font size",
        "editor_font_size_value" to "%1${'d sp}d sp",
        "editor_indent" to "Indent",
        "editor_load_failed" to "Failed to open file",
        "editor_md_preview" to "Preview Markdown",
        "editor_md_show_source" to "Show source",
        "editor_redo" to "Redo",
        "editor_save_and_exit" to "Save and exit",
        "editor_save_and_switch" to "Save and switch",
        "editor_save_conflict_cancel" to "Cancel",
        "editor_save_conflict_diff" to "- Current on disk\n+ Your edits\n%1${'s}s",
        "editor_save_conflict_discard" to "Discard my edits",
        "editor_save_conflict_discarded" to "Edits discarded; reloaded from disk",
        "editor_save_conflict_message" to "This file was modified by the AI or another program after you opened it. Saving now would overwrite those changes.",
        "editor_save_conflict_overwrite" to "Overwrite disk",
        "editor_save_conflict_title" to "File changed on disk",
        "editor_save_failed" to "Save failed",
        "editor_save_success" to "Saved",
        "editor_settings" to "Editor settings",
        "editor_show_indent_guide" to "Show indent guides",
        "editor_show_whitespace" to "Show whitespace",
        "editor_show_wrap_arrow" to "Show word-wrap arrows",
        "editor_undo" to "Undo",
        "editor_unsaved_message" to "This file has unsaved changes. Save them?",
        "editor_unsaved_title" to "Unsaved changes",
        "editor_word_wrap" to "Word wrap",
        "file_browser_clipboard_copy" to "Copy",
        "file_browser_clipboard_cut" to "Cut",
        "file_browser_collapse" to "Collapse",
        "file_browser_create_failed" to "Create failed: name may already exist or the folder is not writable",
        "file_browser_delete_failed" to "Delete failed",
        "file_browser_delete_file_confirm" to "Delete file \"%1${'s\"?}s\"?",
        "file_browser_delete_folder_confirm" to "Delete folder \"%1${'s\" and all of its contents?}s\" and all of its contents?",
        "file_browser_empty" to "Empty folder",
        "file_browser_error" to "Failed to read folder",
        "file_browser_expand" to "Expand",
        "file_browser_name_label" to "Name",
        "file_browser_new_file" to "New file",
        "file_browser_new_folder" to "New folder",
        "file_browser_paste_conflict_message" to "An item named \"%1${'s\" already exists at the destination. Overwrite?}s\" already exists at the destination. Overwrite?",
        "file_browser_paste_conflict_title" to "Same name already exists",
        "file_browser_paste_here" to "Paste here",
        "file_browser_refresh" to "Refresh",
        "file_browser_rename_failed" to "Rename failed: the name may already exist",
        "file_browser_up" to "Up one level",
        "ftp_address_copied" to "Address copied",
        "ftp_allow_anonymous" to "Allow Anonymous",
        "ftp_anonymous_confirm_message" to "Once enabled, any device on the same LAN can read and write your workspace without credentials.",
        "ftp_anonymous_confirm_title" to "Allow anonymous access?",
        "ftp_anonymous_desc" to "Connect without username and password",
        "ftp_auto_start" to "Auto-start on app launch",
        "ftp_auto_start_desc" to "Automatically start FTP service in background on app launch",
        "ftp_config_saved" to "Configuration saved",
        "ftp_config_title" to "Service Configuration",
        "ftp_copy_address" to "Copy address",
        "ftp_error" to "Error: %1${'s}s",
        "ftp_listen_port" to "Listen Port",
        "ftp_login_password" to "Login Password",
        "ftp_login_username" to "Login Username",
        "ftp_not_running" to "Service not running",
        "ftp_password_required" to "Set a username and password first",
        "ftp_reserved_username" to "anonymous is reserved for anonymous login. Use a different username.",
        "ftp_running" to "Running: %1${'s}s",
        "ftp_save_config" to "Save Configuration",
        "ftp_usage_item_1" to "Phone and computer on the same WiFi",
        "ftp_usage_item_2" to "Enter the address below in the computer's file explorer",
        "ftp_usage_item_3" to "Manage projects in the phone's workspace",
        "ftp_usage_title" to "Usage Guide",
        "ftp_user_cache_cleanup_failed" to "Could not clear old FTP login data. The server was not started. Try again.",
        "git_abort_merge_confirm" to "Abort the current merge? Unresolved changes will be discarded and the working tree will be restored.",
        "git_action_abort_merge" to "Abort Merge",
        "git_action_clone" to "Clone",
        "git_action_commit" to "Commit",
        "git_action_copy_path" to "Copy path",
        "git_action_create_branch" to "Create branch",
        "git_action_create_tag" to "Create tag",
        "git_action_delete_branch" to "Delete branch",
        "git_action_delete_dir" to "Delete folder",
        "git_action_delete_file" to "Delete file",
        "git_action_delete_remote_branch" to "Delete remote branch",
        "git_action_delete_tag" to "Delete tag",
        "git_action_init" to "Initialize",
        "git_action_merge" to "Merge Branch",
        "git_action_rename_branch" to "Rename branch",
        "git_action_restore_file" to "Restore file",
        "git_action_revert" to "Revert changes",
        "git_action_revert_all" to "Revert all",
        "git_action_stage_all" to "Stage all",
        "git_action_stash" to "Stash Changes",
        "git_action_unstage_all" to "Unstage all",
        "git_action_view_diff" to "View diff",
        "git_add_to_gitignore" to "Add to .gitignore",
        "git_ahead_count" to "%1${'s to push}s to push",
        "git_base_branch" to "Base branch",
        "git_behind_count" to "%1${'s to pull}s to pull",
        "git_branch_name" to "Branch name",
        "git_branch_synced" to "Synced",
        "git_checked_out" to "Checked out",
        "git_clean" to "Working tree clean",
        "git_clone_desc" to "Clone a remote repository into the current workspace root (directory must be empty). For private repos, configure credentials via the key icon first.",
        "git_clone_dialog_title" to "Clone Git Repository",
        "git_clone_repo" to "Clone Remote Repository",
        "git_clone_url_hint" to "https://github.com/user/repo.git",
        "git_clone_url_label" to "Repository URL",
        "git_cloning_hint" to "Cloning repository, please wait…",
        "git_commit_ai_generate" to "AI Generate",
        "git_commit_amend" to "Amend last commit (--amend)",
        "git_commit_changes" to "Commit Changes",
        "git_commit_count" to "Commits (%1${'s)}s)",
        "git_commit_dialog_title" to "Commit Changes",
        "git_commit_generating" to "Generating…",
        "git_commit_message" to "Commit message",
        "git_commit_needs_identity" to "No commit identity configured. Set your name and email in “Credentials & Identity”.",
        "git_computing_diff" to "Computing diff...",
        "git_conflicted_count" to "Conflicts (%1${'s)}s)",
        "git_copy_full_hash" to "Copy full hash",
        "git_copy_short_hash" to "Copy short hash",
        "git_create_and_switch" to "Create and Switch",
        "git_create_branch_from_commit" to "Create Branch from Commit",
        "git_create_tag_at_commit" to "Create Tag at Commit",
        "git_create_tag_desc" to "Create lightweight tag from current HEAD (%1${'s)}s)",
        "git_create_tag_title" to "New Tag",
        "git_create_tracking_branch" to "Create a local tracking branch from remote branch %1${'s and switch?}s and switch?",
        "git_credentials_and_identity" to "Credentials & Identity",
        "git_credentials_count" to "Credentials (%1${'d)}d)",
        "git_current_branch" to "Current branch",
        "git_delete_branch" to "Delete Branch",
        "git_delete_local_branch_confirm" to "Delete local branch %1${'s?}s?",
        "git_delete_remote_branch" to "Delete Remote Branch",
        "git_delete_remote_branch_confirm" to "Delete remote branch %1${'s? This action cannot be undone.}s? This action cannot be undone.",
        "git_delete_tag" to "Delete Tag",
        "git_delete_tag_confirm" to "Delete local tag %1${'s?}s?",
        "git_delete_untracked_confirm" to "Delete %1${'s? It has never been committed, so Git cannot recover it after deletion.}s? It has never been committed, so Git cannot recover it after deletion.",
        "git_detached_head_label" to "detached HEAD: not on any branch",
        "git_detached_head_warning" to "Switching to tag %1${'s will enter detached HEAD state — not on any branch, new commits won't belong to any branch. Continue?}s will enter detached HEAD state — not on any branch, new commits won't belong to any branch. Continue?",
        "git_expanded" to "Expanded",
        "git_files_changed" to "%1${'s files changed}s files changed",
        "git_has_changes" to "Working tree has changes",
        "git_identity_desc" to "Used as author for every commit. Applies globally in the container, shared across all workspaces.",
        "git_identity_title" to "Commit Identity",
        "git_ignore_dir" to "Ignore directory (%1${'s/)}s/)",
        "git_ignore_extension" to "Ignore same extension (*.%1${'s)}s)",
        "git_ignore_file" to "Ignore this file (%1${'s)}s)",
        "git_init_desc_primary" to "Initialize a new repository here to start version control",
        "git_init_desc_secondary" to "Or pick an existing repository from a subdirectory",
        "git_init_repo" to "Initialize New Repository",
        "git_latest_commit" to "Latest commit",
        "git_latest_date" to "Latest date",
        "git_load_more_commits" to "Pull up to load more commits",
        "git_loading_branches" to "Loading branch list…",
        "git_loading_files" to "Loading changed files...",
        "git_merge_commit" to "Merge commit",
        "git_merge_confirm" to "Merge branch %1${'s into current branch (%2$s)?}s into current branch (%2${'s)?}s)?",
        "git_merge_into" to "Merge into %1${'s}s",
        "git_merge_no_ff" to "Create a merge commit (--no-ff)",
        "git_merge_pending_hint" to "There are no unresolved conflicts. Review and stage the changes you want to keep, then commit to complete the merge.",
        "git_merge_pending_title" to "Merge Awaiting Commit",
        "git_modified_count" to "Modified (%1${'s)}s)",
        "git_modified_label" to "Modified",
        "git_new" to "New",
        "git_new_branch" to "New Branch",
        "git_new_branch_name" to "New branch name",
        "git_no_branch" to "(no branch)",
        "git_no_branches" to "No branches",
        "git_no_checked_out_branch" to "No branch checked out",
        "git_no_commits" to "No commits yet",
        "git_no_files_changed" to "No files changed in this commit",
        "git_no_identity" to "No identity configured, cannot commit. Please fill in user.name / user.email in the \"Credentials\" page.",
        "git_no_more_commits" to "No more commits",
        "git_no_upstream" to "No upstream set",
        "git_not_a_repo" to "Current workspace is not a Git repository yet",
        "git_open_in_editor" to "Open in Editor",
        "git_pick_existing_repo" to "Pick Existing Repository",
        "git_pick_repo_empty" to "No directories available in the current workspace",
        "git_pull" to "Pull",
        "git_pull_continue" to "Continue Pull",
        "git_pull_dirty_confirm" to "The working tree has uncommitted changes. Pulling may cause conflicts. Commit or stage them first if possible. Continue pulling?",
        "git_push" to "Push",
        "git_push_keep_master" to "Keep master & Push",
        "git_push_master_confirm_desc" to "Current local branch is \"master\". GitHub and GitLab recommend using \"main\" as the default branch. Would you like to rename it to \"main\" before pushing?",
        "git_push_master_confirm_title" to "Branch Naming Suggestion",
        "git_push_rename_to_main" to "Rename to main & Push",
        "git_recent_commit" to "Recent commit",
        "git_refresh" to "Refresh",
        "git_remote" to "Remote",
        "git_remote_url" to "remote.origin.url",
        "git_rename_branch" to "Rename Branch",
        "git_rename_to" to "Rename %1${'s to:}s to:",
        "git_repo_not_repo" to "Not a repo",
        "git_repo_selector_title" to "Select Repository",
        "git_repo_url_title" to "Repository URL",
        "git_reset_confirm" to "Reset current branch to commit %1${'s?}s?",
        "git_reset_mixed" to "Mixed (Keep working tree, unstage)",
        "git_reset_soft" to "Soft (Keep working tree and staged)",
        "git_reset_to_commit" to "Reset to this Commit",
        "git_restore_file_confirm" to "Restore the deleted %1${'s? The file will be brought back from the last commit.}s? The file will be brought back from the last commit.",
        "git_revert_all_confirm" to "Discard all unstaged changes (%1${'d files) and restore them to the last staged or committed content? Staged content is unaffected, but discarded changes cannot be recovered.}d files) and restore them to the last staged or committed content? Staged content is unaffected, but discarded changes cannot be recovered.",
        "git_revert_confirm_head" to "Restore %1${'s to its last committed content? Both staged and unstaged changes to this file will be lost and cannot be recovered.}s to its last committed content? Both staged and unstaged changes to this file will be lost and cannot be recovered.",
        "git_revert_confirm_worktree" to "Restore %1${'s to its last staged or committed content? Unstaged changes will be lost and cannot be recovered.}s to its last staged or committed content? Unstaged changes will be lost and cannot be recovered.",
        "git_search_commits_hint" to "Search commit message or author…",
        "git_stage" to "Stage",
        "git_stage_all" to "Stage All",
        "git_staged_count" to "Staged (%1${'s)}s)",
        "git_staged_label" to "Staged",
        "git_stash_apply" to "Apply",
        "git_stash_clear" to "Clear All",
        "git_stash_clear_confirm" to "Clear all stashes? This action cannot be undone.",
        "git_stash_count" to "Stashes (%1${'d)}d)",
        "git_stash_drop" to "Drop",
        "git_stash_drop_confirm" to "Drop stash %1${'s? This action cannot be undone.}s? This action cannot be undone.",
        "git_stash_empty" to "No stashes found",
        "git_stash_include_untracked" to "Include untracked files (-u)",
        "git_stash_message_hint" to "Stash message (optional)",
        "git_stash_pop" to "Pop",
        "git_stash_title" to "Stashes",
        "git_status_clean" to "Working tree clean, no changes",
        "git_switch_branch" to "Switch Branch",
        "git_switch_confirm" to "Switch to %1${'s?}s?",
        "git_switch_repo" to "Switch repository",
        "git_tab_branches" to "Branches",
        "git_tab_commits" to "Commits",
        "git_tab_credentials" to "Credentials",
        "git_tab_identity" to "Identity",
        "git_tab_stash" to "Stash",
        "git_tab_status" to "Status",
        "git_tag_name" to "Tag name",
        "git_tags" to "Tags",
        "git_title" to "Git",
        "git_toast_action_failed" to "%1${'s failed: %2$s}s failed: %2${'s}s",
        "git_toast_action_refresh_failed" to "%1${'s (refresh failed)}s (refresh failed)",
        "git_toast_action_success" to "%1${'s successful}s successful",
        "git_toast_added_to_gitignore" to "Added to .gitignore",
        "git_toast_checkout_failed" to "Switch failed: %1${'s}s",
        "git_toast_checkout_success" to "Switched to %1${'s}s",
        "git_toast_diff_failed" to "Failed to load diff",
        "git_toast_generate_commit_failed" to "Failed to generate commit message, please check model or network",
        "git_toast_load_branches_failed" to "Failed to load branches: %1${'s}s",
        "git_toast_load_more_failed" to "Failed to load more: %1${'s}s",
        "git_toast_no_diff_for_commit" to "No changes found to generate commit message",
        "git_toast_no_remote_pull" to "No remote repository configured, cannot pull",
        "git_toast_no_remote_push" to "No remote repository configured, cannot push",
        "git_toast_not_repo" to "This directory is not a Git repository",
        "git_toast_path_copied" to "Path copied",
        "git_toast_refresh_failed" to "Refresh failed: %1${'s}s",
        "git_toast_switch_repo_failed" to "Switch repository failed: %1${'s}s",
        "git_tracking_branch" to "Tracking %1${'s}s",
        "git_unstage" to "Unstage",
        "git_unstage_all_confirm" to "All files will be unstaged, but their contents will not be deleted. Continue?",
        "git_untracked_count" to "Untracked (%1${'s)}s)",
        "git_untracked_label" to "Untracked",
        "image_gen_no_models" to "No models available. Please add and configure a model in \"AI Vendors\" (must support image output, e.g. gpt-image-1 / dall-e-3) before selecting an image generation model.",
        "language_follow_system" to "Follow System",
        "log_clear_filter" to "Clear filter",
        "log_copied" to "Copied",
        "log_copy" to "Copy",
        "log_copy_line" to "Copy this line",
        "log_copy_menu_title" to "Copy Log",
        "log_copy_message" to "Copy message",
        "log_copy_with_stack" to "Copy with stack trace",
        "log_file_location" to "Log files are located at Android/data//files/logs/.",
        "log_files" to "Log Files",
        "log_follow_latest" to "Live",
        "log_level" to "Log Level",
        "log_level_desc" to "Logs below this level will be ignored. Use VERBOSE during development (log all); NONE to disable logging.",
        "log_mcp_prefix" to "MCP Log: %1${'s}s",
        "log_no_match" to "No logs match the current filter",
        "log_search_hint" to "Search logs",
        "log_search_results" to "%1${'s matches}s matches",
        "log_show_last_lines" to "Showing last %1${'s/%2$s lines}s/%2${'s lines}s lines",
        "log_show_lines" to "Showing %1${'s lines}s lines",
        "mcp_add" to "Add MCP",
        "mcp_add_arg" to "Add Argument",
        "mcp_add_env_var" to "Add Environment Variable",
        "mcp_add_from_json" to "Import from JSON",
        "mcp_add_from_json_desc" to "Paste a standard mcpServers config to import in bulk",
        "mcp_add_header" to "Add Header",
        "mcp_add_manual" to "Fill in manually",
        "mcp_add_manual_desc" to "Enter URL, headers, or launch command one by one",
        "mcp_add_sheet_title" to "Add MCP server",
        "mcp_arg_hint" to "e.g. -y or @modelcontextprotocol/server-xxx",
        "mcp_arg_value" to "Argument Value",
        "mcp_args" to "Arguments",
        "mcp_auth_advanced" to "Advanced OAuth options",
        "mcp_auth_cancel" to "Cancel authorization",
        "mcp_auth_client_id" to "client_id (optional)",
        "mcp_auth_client_id_hint" to "Pre-registered client ID; leave blank to try dynamic registration",
        "mcp_auth_desc" to "For remote MCP servers that require browser sign-in",
        "mcp_auth_enable" to "OAuth authentication",
        "mcp_auth_failed" to "Authorization failed: %1${'s}s",
        "mcp_auth_in_progress" to "Waiting for browser authorization…",
        "mcp_auth_in_progress_hint" to "Opened sign-in page in browser; will reconnect automatically once completed",
        "mcp_auth_login" to "Sign in / Re-authorize",
        "mcp_auth_logout" to "Sign out",
        "mcp_auth_reauthorize" to "Re-authorize",
        "mcp_auth_save_first" to "Save the server configuration first to sign in here",
        "mcp_auth_server" to "Authorization server (optional)",
        "mcp_auth_server_hint" to "Override the auto-discovered authorization server",
        "mcp_auth_status_authorized" to "Signed in",
        "mcp_auth_status_authorizing" to "Authorizing",
        "mcp_auth_status_label" to "Authorization status",
        "mcp_auth_status_unauthorized" to "Not signed in",
        "mcp_auth_success" to "Authorized and connected",
        "mcp_basic_settings" to "Basic Settings",
        "mcp_batch_delete_confirm" to "Delete the selected %1${'d MCP servers?}d MCP servers?",
        "mcp_batch_delete_title" to "Delete MCP Servers",
        "mcp_command" to "Command",
        "mcp_command_hint" to "e.g. npx",
        "mcp_connected" to "Connected",
        "mcp_connecting" to "Connecting…",
        "mcp_connection_failed" to "Connection failed",
        "mcp_custom_headers" to "Custom Headers",
        "mcp_details" to "Details",
        "mcp_disabled" to "Disabled",
        "mcp_edit" to "Edit MCP",
        "mcp_empty" to "No MCP servers configured",
        "mcp_empty_hint" to "Tap + in the top right to add a remote HTTP or local stdio server",
        "mcp_enabled" to "Enabled",
        "mcp_env_name" to "Variable Name",
        "mcp_env_name_hint" to "e.g. API_KEY",
        "mcp_env_value" to "Variable Value",
        "mcp_env_value_hint" to "e.g. sk-xxxx",
        "mcp_env_vars" to "Environment Variables",
        "mcp_header_name" to "Header Name",
        "mcp_header_name_hint" to "e.g. Authorization",
        "mcp_header_value" to "Header Value",
        "mcp_header_value_hint" to "e.g. Bearer xxxxxx",
        "mcp_json_import_action" to "Import",
        "mcp_json_import_empty" to "No MCP server was parsed",
        "mcp_json_import_hint" to "Paste an mcpServers config, e.g. {\"mcpServers\":{\"demo\":{\"url\":\"https://...\"}}}",
        "mcp_json_import_title" to "Import from JSON",
        "mcp_local_stdio" to "Local stdio",
        "mcp_name_hint" to "e.g. fetch, github, gitlab",
        "mcp_name_invalid" to "Name can only contain letters, digits, underscores, and hyphens",
        "mcp_needs_auth" to "Sign-in required",
        "mcp_no_args" to "No extra arguments",
        "mcp_no_custom_headers" to "No custom headers configured",
        "mcp_no_description" to "No description",
        "mcp_no_env_vars" to "No custom environment variables",
        "mcp_no_tools_hint" to "No tools detected, tap the refresh button in the top right to retry",
        "mcp_not_connected" to "Not connected",
        "mcp_refresh_tools" to "Refresh Tools",
        "mcp_remote_http" to "Remote HTTP",
        "mcp_save_first_hint" to "Please save and connect to the server first to view the tool list",
        "mcp_scope_global" to "Global",
        "mcp_scope_label" to "Scope",
        "mcp_scope_project" to "Project",
        "mcp_search_empty" to "No matching MCP servers found",
        "mcp_search_placeholder" to "Search MCP servers…",
        "mcp_server_url" to "Server URL",
        "mcp_server_url_hint" to "e.g. https://api.example.com/sse",
        "mcp_tools_count" to "%1${'d tools}d tools",
        "mcp_tools_disable_all" to "Disable all",
        "mcp_tools_enable_all" to "Enable all",
        "mcp_tools_search" to "Search tool name or description…",
        "mcp_tools_search_empty" to "No matching tools found",
        "mcp_tools_summary" to "%1${'d / %2$d tools enabled}d / %2${'d tools enabled}d tools enabled",
        "mcp_transport_type" to "Transport Type",
        "mcp_type_stdio" to "stdio",
        "mcp_view_logs" to "View this MCP's logs",
        "network_error_connect" to "Connection refused: check the Base URL and port",
        "network_error_dns" to "DNS resolution failed: the host has no usable address. Check your network, Wi-Fi DNS, or proxy settings",
        "network_error_ssl" to "SSL handshake failed: check the certificate or proxy settings",
        "network_error_timeout" to "Request timed out: slow network or no response from the server",
        "notification_agent_live_title_multi" to "%1${'d tasks running}d tasks running",
        "notification_approval_action_allow" to "Allow",
        "notification_approval_action_approve_plan" to "Approve",
        "notification_approval_action_reject" to "Reject",
        "notification_approval_label_permission" to "Permission required",
        "notification_approval_label_plan" to "Plan approval",
        "notification_approval_label_question" to "Answer required",
        "notification_approval_multi_title" to "%1${'d tasks awaiting confirmation}d tasks awaiting confirmation",
        "notification_approval_needed" to "Approval needed",
        "notification_approval_title" to "AI is waiting for your confirmation",
        "notification_channel_agent_approval" to "Approval notifications",
        "notification_channel_agent_approval_desc" to "Notifications shown when the AI requests permission, asks a question, or waits for plan approval in the background",
        "notification_channel_agent_complete" to "Agent completion notifications",
        "notification_channel_agent_complete_desc" to "Notifications shown when the agent finishes in background",
        "notification_channel_agent_live" to "AI live progress",
        "notification_channel_agent_live_desc" to "Notifications showing the current progress while the AI works in the background",
        "notification_channel_terminal_service" to "Terminal Services",
        "notification_channel_terminal_service_desc" to "Notifications for background terminal tasks",
        "notification_keepalive_enabled" to "Keepalive enabled",
        "notification_keepalive_persistent_sessions" to "Keepalive · terminal tasks: %1${'d}d",
        "notification_keepalive_running_sessions" to "Running terminal tasks: %1${'d}d",
        "onboarding_choose_model_in_sheet_desc" to "Tap the model you just added to set it for your chat session.",
        "onboarding_choose_model_in_sheet_title" to "Choose Model",
        "onboarding_config_provider_desc" to "Tap to open the AI vendors list and start setting up your model vendors.",
        "onboarding_config_provider_title" to "AI Vendors",
        "onboarding_done" to "Done",
        "onboarding_enter_settings_desc" to "Tap to open App settings to manage AI vendors and API keys.",
        "onboarding_enter_settings_title" to "Go to Settings",
        "onboarding_next" to "Next",
        "onboarding_open_model_picker_desc" to "Back on home, tap the model icon next to the input box to open the selection panel.",
        "onboarding_open_model_picker_title" to "Open Model Panel",
        "onboarding_open_sidebar_desc" to "Tap the top-left button to open sidebar and navigate to settings to configure models.",
        "onboarding_open_sidebar_title" to "Open Sidebar",
        "onboarding_provider_add_desc" to "Tap “+” to add a vendor, supporting official vendors like OpenAI, Claude, Gemini, DeepSeek, or custom relays.",
        "onboarding_provider_add_title" to "Add Vendor",
        "onboarding_provider_config_info_desc" to "Enter your API key. Official presets come with base URLs prefilled; custom relays can set a custom Base URL.",
        "onboarding_provider_config_info_title" to "Configuration Info",
        "onboarding_provider_fetch_models_desc" to "Once the key is configured, tap “Fetch models” to sync available models.",
        "onboarding_provider_fetch_models_title" to "Fetch Models",
        "onboarding_provider_select_fetched_model_desc" to "Models retrieved. Tap the add button to add this model to the vendor.",
        "onboarding_provider_select_fetched_model_title" to "Add Fetched Model",
        "onboarding_send_message_desc" to "Model ready. Type your coding tasks or questions here to start collaborating with the AI assistant.",
        "onboarding_send_message_title" to "Start Chatting",
        "onboarding_skip" to "Skip",
        "onboarding_step_badge" to "Guide %1${'d/%2$d}d/%2${'d}d",
        "perm_current_project" to "Current project: %1${'s}s",
        "perm_current_project_none" to "Current project (not selected)",
        "perm_deny" to "Deny",
        "perm_disable_safety_interception" to "Disable safety interception",
        "perm_disable_safety_interception_desc" to "When on, AUTO mode stops blocking catastrophic deletes and Shizuku prompts",
        "perm_entire_tool" to "Entire tool",
        "perm_global" to "Global",
        "perm_no_global_rules" to "No global rules",
        "perm_no_project_rules" to "No rules for this project",
        "perm_promote_to_global" to "Promote to global",
        "perm_rules_short" to "Choosing \"Always Allow\" in chat creates remembered rules matched by command or path",
        "perm_safety_confirm_action" to "Confirm (%1${'d)}d)",
        "perm_safety_confirm_action_ready" to "Confirm",
        "perm_safety_confirm_message" to "Once disabled, AUTO mode no longer blocks catastrophic deletes and no longer prompts for the Shizuku tool. Proceed with caution.",
        "perm_safety_confirm_title" to "Disable safety interception?",
        "perm_safety_group" to "Safety",
        "plan_mode_already_active" to "Already in PLAN mode.",
        "plan_mode_approved_auto" to "The plan was approved and AUTO mode restored. Implement within the approved scope and follow AUTO permission rules.",
        "plan_mode_approved_build" to "The plan was approved. BUILD mode is active; implement within the approved scope.",
        "plan_mode_enter_action" to "Enter PLAN mode",
        "plan_mode_entered" to "Entered PLAN mode.",
        "plan_mode_exit_action" to "Exit PLAN mode",
        "plan_mode_exited" to "Exited PLAN and restored %1${'s mode.}s mode.",
        "plan_mode_external_auto" to "The user switched to AUTO. Continue within the current task scope and mode permissions.",
        "plan_mode_external_build" to "The user switched to BUILD. Continue within the current task scope and mode permissions.",
        "plan_mode_goal_active" to "A goal is running autonomously. Entering or exiting PLAN is unavailable.",
        "plan_mode_invalid_action" to "Invalid action: %1${'s. Supported values: enter or exit.}s. Supported values: enter or exit.",
        "plan_mode_not_active" to "Cannot exit PLAN: the session is not in PLAN mode.",
        "plan_mode_reason_unspecified" to "No reason provided",
        "plan_mode_refinement_requested" to "The user chose to refine the plan. Remain in PLAN and wait for specific feedback before making changes or requesting to exit again.",
        "plan_mode_request_details" to "Action: %1${'s\nReason: %2$s}s\nReason: %2${'s}s",
        "plan_mode_request_summary" to "Request to %1${'s}s",
        "plan_mode_request_title" to "Switch planning mode",
        "plan_mode_session_missing" to "Session not found. Cannot switch modes.",
        "plan_mode_session_required" to "No session is associated with this request. Cannot switch modes.",
        "prompts_add_prompt" to "Add fragment",
        "prompts_batch_delete_confirm" to "Delete the selected %1${'d custom prompt fragments? Their numbers will fall back to the next source.}d custom prompt fragments? Their numbers will fall back to the next source.",
        "prompts_batch_delete_title" to "Delete Prompts",
        "prompts_disable_builtin" to "Disable all built-in prompts",
        "prompts_disable_builtin_hint" to "Keep only your own fragments; you must supply every rule yourself",
        "prompts_editor_edit" to "Edit prompt",
        "prompts_editor_new" to "New prompt",
        "prompts_empty" to "No fragments available",
        "prompts_field_basic" to "Basic",
        "prompts_field_content" to "Fragment content",
        "prompts_field_content_placeholder" to "What the model should follow",
        "prompts_field_number" to "Number",
        "prompts_field_number_placeholder" to "Two digits, e.g. 35",
        "prompts_field_summary" to "Summary (optional)",
        "prompts_field_summary_placeholder" to "Brief description; not included in the prompt",
        "prompts_field_title" to "Name",
        "prompts_field_title_placeholder" to "e.g. My safety rules",
        "prompts_help" to "Help",
        "prompts_reset_confirm" to "Deletes every custom prompt fragment in this project and the global layer; each number falls back to the built-in prompt. This cannot be undone.",
        "prompts_reset_custom" to "Reset custom prompts",
        "prompts_sort_long_press" to "Long-press to reorder",
        "prompts_source_builtin" to "Built-in",
        "prompts_source_global" to "Global",
        "prompts_source_local" to "Local",
        "prompts_source_project" to "Project",
        "prompts_title" to "Prompts",
        "provider_add" to "Add Vendor",
        "provider_add_all_models" to "Add All",
        "provider_add_all_models_confirm" to "Add all %1${'d fetched models to this provider?}d fetched models to this provider?",
        "provider_add_all_models_title" to "Add All Models",
        "provider_add_model" to "Add Model",
        "provider_add_sheet_title" to "Add Vendor",
        "provider_api_key" to "API Key",
        "provider_api_key_required" to "Please enter an API Key first",
        "provider_base_url" to "Base URL",
        "provider_brand_doubao" to "Doubao",
        "provider_brand_hunyuan" to "Hunyuan",
        "provider_brand_siliconflow" to "SiliconFlow",
        "provider_brand_tongyi_qianwen" to "Tongyi Qianwen",
        "provider_brand_zhipu" to "Zhipu",
        "provider_cache_anthropic_subtitle" to "Mark system/tools/messages prefixes with explicit cache breakpoints to cut repeated input cost",
        "provider_cache_anthropic_title" to "Anthropic cache breakpoints",
        "provider_cache_openai_chat_subtitle" to "Send prompt_cache_key with requests",
        "provider_cache_openai_chat_title" to "OpenAI-compatible cache key",
        "provider_config" to "Config",
        "provider_custom" to "Custom Vendor",
        "provider_dashboard_clear_script" to "None (Clear script)",
        "provider_dashboard_loading" to "Loading dashboard...",
        "provider_dashboard_no_scripts" to "No scripts found in ~/.aicode/scripts/",
        "provider_dashboard_preview_title" to "Dashboard Preview",
        "provider_dashboard_raw_output" to "Raw Output",
        "provider_dashboard_run_test" to "Run Test",
        "provider_dashboard_script" to "Dashboard Script",
        "provider_dashboard_script_desc" to "Execute and preview dashboard output",
        "provider_dashboard_script_placeholder" to "e.g. demo_balance.py",
        "provider_dashboard_select_script" to "Select Script",
        "provider_dashboard_select_title" to "Select Dashboard Script",
        "provider_dashboard_test_btn" to "Run Test",
        "provider_dashboard_test_failed" to "Execution Failed",
        "provider_edit" to "Edit Vendor",
        "provider_edit_model" to "Edit Model",
        "provider_fetch_failed" to "Fetch failed: %1${'s}s",
        "provider_fetch_http_error" to "HTTP %1${'d · %2$dms}d · %2${'dms}dms",
        "provider_fetch_missing_fields" to "Response is missing the list field",
        "provider_fetch_missing_fields_detail" to "Response missing list field (models or data): %1${'s}s",
        "provider_fetch_models" to "Fetch Models",
        "provider_fetch_success" to "Fetched %1${'d models · %2$dms}d models · %2${'dms}dms",
        "provider_filter_models_hint" to "Filter models by name",
        "provider_full_url" to "Full URL",
        "provider_full_url_desc" to "Base URL is the full request address, no default path appended",
        "provider_headers_add" to "Add header",
        "provider_headers_count" to "%1${'d headers configured}d headers configured",
        "provider_headers_desc" to "Completely override the same-name default header for this vendor",
        "provider_headers_empty" to "No headers yet; add one below",
        "provider_headers_manage" to "Manage",
        "provider_headers_name" to "Header Name",
        "provider_headers_name_hint" to "e.g. Authorization",
        "provider_headers_remove" to "Remove header",
        "provider_headers_title" to "Custom Headers",
        "provider_headers_value" to "Value",
        "provider_headers_value_hint" to "e.g. Bearer sk-xxx",
        "provider_hide_api_key" to "Hide API Key",
        "provider_interactions_api" to "Interactions API (new)",
        "provider_model_already_added" to "This model has already been added",
        "provider_model_cap_image_output" to "Image Output",
        "provider_model_cap_vision" to "Image Input",
        "provider_model_capabilities" to "Capabilities",
        "provider_model_capability_reasoning" to "Reasoning",
        "provider_model_capability_tools" to "Tool Calling",
        "provider_model_context_input" to "Input Window (tokens)",
        "provider_model_context_output" to "Output Window (tokens)",
        "provider_model_context_total" to "Total Context Window (tokens)",
        "provider_model_icon" to "Model icon",
        "provider_model_name" to "Model Name",
        "provider_model_price_cache_read" to "Cache Read Price (USD/1M tokens)",
        "provider_model_price_cache_write" to "Cache Write Price (USD/1M tokens)",
        "provider_model_price_input" to "Input Price (USD/1M tokens)",
        "provider_model_price_output" to "Output Price (USD/1M tokens)",
        "provider_model_reasoning_can_disable" to "Thinking can be turned off",
        "provider_model_reasoning_hint" to "Tick only the levels this model really accepts; leave all unticked to use the built-in capability table",
        "provider_model_section_price" to "Pricing",
        "provider_model_section_reasoning" to "Reasoning levels",
        "provider_model_window_invalid" to "Enter a valid positive integer, or leave blank to use the automatically detected value",
        "provider_models_aligned_removed" to "Removed %1${'d model(s) no longer offered by the vendor}d model(s) no longer offered by the vendor",
        "provider_models_batch_delete" to "Delete",
        "provider_models_batch_delete_confirm" to "Are you sure you want to delete the selected %1${'d models?}d models?",
        "provider_models_batch_delete_title" to "Delete Models",
        "provider_models_batch_test" to "Test",
        "provider_models_count" to "Models (%1${'s)}s)",
        "provider_models_count_tag" to "%1${'d models}d models",
        "provider_models_deselect_all" to "Deselect All",
        "provider_models_select" to "Select",
        "provider_models_select_all" to "Select All",
        "provider_models_selected_count" to "%1${'d selected}d selected",
        "provider_multi_key_add" to "Add key",
        "provider_multi_key_cooldown" to "Cooldown",
        "provider_multi_key_cooldown_desc" to "How long a switched-away key stays out of the pool",
        "provider_multi_key_cooldown_off" to "None",
        "provider_multi_key_cooldown_value" to "%1${'d min}d min",
        "provider_multi_key_empty" to "Tap to add the first key",
        "provider_multi_key_failover" to "Failover",
        "provider_multi_key_item" to "Key #%1${'d}d",
        "provider_multi_key_list" to "Key List",
        "provider_multi_key_manage" to "Manage Keys",
        "provider_multi_key_status_codes" to "Auto-Switch Status Codes",
        "provider_multi_key_status_codes_hint" to "Comma-separated; leave empty for default 401,402,403,429",
        "provider_multi_key_strategy" to "Selection Strategy",
        "provider_multi_key_strategy_round_robin" to "Round Robin",
        "provider_multi_key_strategy_round_robin_desc" to "New sessions start on rotating keys to spread quota; a session keeps its key so prompt cache still hits",
        "provider_multi_key_strategy_sequential" to "Sequential",
        "provider_multi_key_strategy_sequential_desc" to "Always use the earliest key; move on only after the failure threshold",
        "provider_multi_key_subtitle" to "Use several keys for one vendor and switch automatically when a key stops working",
        "provider_multi_key_summary" to "%1${'d keys · %2$s}d keys · %2${'s}s",
        "provider_multi_key_title" to "Multi-Key Mode",
        "provider_multi_key_weight" to "Scheduling weight",
        "provider_multi_key_weight_hint" to "Default 1 · ≤ 0 excluded · Max 1,000,000",
        "provider_new" to "New Vendor",
        "provider_no_matching_models" to "No matching models",
        "provider_no_models" to "No Models",
        "provider_no_models_hint" to "Tap the button in the top-right to add a model",
        "provider_please_wait" to "Please wait…",
        "provider_preset_empty" to "No matching presets",
        "provider_preset_loading" to "Loading presets…",
        "provider_proxy_enable" to "Configure a dedicated proxy for this vendor",
        "provider_proxy_enable_desc" to "When enabled, requests to this vendor use its own proxy; otherwise the global proxy applies",
        "provider_proxy_off" to "Off",
        "provider_proxy_section" to "Proxy (optional)",
        "provider_register_now" to "Register",
        "provider_response_api" to "Response API (new)",
        "provider_script_param_add" to "Add parameter",
        "provider_script_param_key" to "Parameter name (KEY)",
        "provider_script_param_key_hint" to "e.g. ACCOUNT_ID",
        "provider_script_param_manage" to "Manage",
        "provider_script_param_remove" to "Remove parameter",
        "provider_script_param_value" to "Parameter value",
        "provider_script_param_value_hint" to "e.g. acc_12345",
        "provider_script_params_count" to "%1${'d parameters configured}d parameters configured",
        "provider_script_params_desc" to "Injected into the panel script as AICODE_KEY_ environment variables",
        "provider_script_params_empty" to "No parameters yet. Tap below to add",
        "provider_script_params_title" to "Script Parameters",
        "provider_section_basic" to "General",
        "provider_section_dashboard" to "Custom Dashboard",
        "provider_section_options" to "Options",
        "provider_section_type" to "Upstream Format",
        "provider_show_api_key" to "Show API Key",
        "provider_sort_long_press" to "Long press to reorder",
        "provider_test" to "Test",
        "provider_test_detail_title" to "Test Debug Details",
        "provider_test_error_detail" to "Error Stack",
        "provider_test_req_body" to "Request Body",
        "provider_test_req_headers" to "Request Headers",
        "provider_test_req_url" to "Request URL",
        "provider_test_resp_body" to "Response Body",
        "provider_test_resp_headers" to "Response Headers",
        "provider_test_success" to "Connected · %1${'dms}dms",
        "providers_batch_delete_confirm" to "Delete the selected %1${'d providers? This cannot be undone.}d providers? This cannot be undone.",
        "providers_batch_delete_title" to "Delete Providers",
        "providers_empty" to "No vendors yet, tap + to add one",
        "proxy_auth" to "Authentication (optional)",
        "proxy_enable" to "Enable proxy",
        "proxy_enable_subtitle" to "Route App network requests and container commands through the proxy",
        "proxy_host" to "Server address",
        "proxy_host_placeholder" to "e.g. 127.0.0.1",
        "proxy_no_proxy" to "No-proxy addresses",
        "proxy_no_proxy_hint" to "Comma-separated; supports wildcards, host:port and CIDR ranges",
        "proxy_password" to "Password (optional)",
        "proxy_port" to "Port",
        "proxy_port_placeholder" to "e.g. 7890",
        "proxy_server" to "Proxy server",
        "proxy_test" to "Test proxy",
        "proxy_test_connect_failed" to "Failed to connect to proxy: %1${'s}s",
        "proxy_test_http_error" to "Proxy returned unexpected status HTTP %1${'d: %2$s}d: %2${'s}s",
        "proxy_test_invalid_config" to "Enter a valid server address and port (1-65535) first",
        "proxy_test_ok" to "Proxy works (HTTP %1${'d)}d)",
        "proxy_test_title" to "Connectivity test",
        "proxy_test_url_label" to "Test URL",
        "proxy_test_url_placeholder" to "https://www.google.com",
        "proxy_testing" to "Testing…",
        "proxy_title" to "Network Proxy",
        "proxy_type" to "Proxy type",
        "proxy_type_http" to "HTTP",
        "proxy_type_socks5" to "SOCKS5",
        "proxy_username" to "Account (optional)",
        "remote_add_channel_first" to "Please add a remote channel in \"Connections\" first.",
        "remote_add_connection" to "Add Connection Channel",
        "remote_add_workspace" to "Add Mount",
        "remote_auth_key" to "Key",
        "remote_auth_password" to "Password",
        "remote_auto_connect_and_sync" to "Auto-connect and sync this workspace on app start",
        "remote_auto_connect_on_start" to "Auto-connect on app start",
        "remote_browse_dir" to "Browse Directory",
        "remote_clear_index_done" to "Sync record cleared",
        "remote_confirm_select" to "Confirm Selection",
        "remote_connect_failed" to "Connection failed: %1${'s}s",
        "remote_connect_success" to "Connected",
        "remote_connection_name_hint" to "Connection name",
        "remote_connection_sort_long_press" to "Long press to reorder",
        "remote_current_path" to "Current path: %1${'s}s",
        "remote_delete_connection_confirm" to "Delete connection “%1${'s”? Sync configurations using it will become unavailable.}s”? Sync configurations using it will become unavailable.",
        "remote_delete_mount_confirm" to "Delete workspace mapping “%1${'s”? Local and remote files will not be deleted.}s”? Local and remote files will not be deleted.",
        "remote_download_all_failed" to "Full download failed: %1${'s}s",
        "remote_download_all_success" to "Full download complete",
        "remote_edit_connection" to "Edit Connection Channel",
        "remote_edit_workspace" to "Edit Mount",
        "remote_go_add" to "Go Add",
        "remote_hint_title" to "Hint",
        "remote_host_address" to "Host",
        "remote_key_passphrase" to "Passphrase (optional)",
        "remote_link_channel" to "Link Remote Connection Channel",
        "remote_load_failed" to "Load failed: %1${'s}s",
        "remote_login_key" to "Login key",
        "remote_map_to_local" to "Map to Local Workspace",
        "remote_mount_already_exists" to "This mount already exists: same channel, remote directory and workspace combination",
        "remote_no_connections" to "No connections yet",
        "remote_no_connections_desc" to "Add an SFTP / FTP connection",
        "remote_no_keys_hint" to "No keys yet, add one in the SSH Host Keys page",
        "remote_no_local_workspace" to "No local workspaces, please create one in the sidebar first.",
        "remote_no_workspaces" to "No mounted directories yet",
        "remote_no_workspaces_desc" to "Local changes auto-sync to the remote; remote files can be downloaded manually",
        "remote_parent_dir" to ".. (parent)",
        "remote_password" to "Password",
        "remote_port" to "Port",
        "remote_select_channel" to "Select Remote Channel",
        "remote_select_key" to "Select key",
        "remote_select_local_workspace" to "Select Local Workspace",
        "remote_select_remote_dir" to "Select Remote Directory",
        "remote_tab_connections" to "Connections",
        "remote_tab_ftp" to "Built-in FTP",
        "remote_tab_mounts" to "Mounted Directories",
        "remote_tab_sync" to "Sync Settings",
        "remote_target_dir" to "Remote Target Directory",
        "remote_test_connection" to "Test Connection",
        "remote_toggle_password" to "Toggle password visibility",
        "remote_unknown_error" to "Unknown error",
        "remote_upload_failed" to "Upload failed: %1${'s}s",
        "remote_upload_full_failed" to "Full upload failed: %1${'s}s",
        "remote_upload_full_success" to "Full upload complete",
        "remote_upload_success" to "Upload complete",
        "remote_workspace_title" to "Connections & Sync",
        "retry_error_connection_refused" to "Connection refused",
        "retry_error_connection_reset" to "Connection interrupted",
        "retry_error_dns_failed" to "DNS resolution failed",
        "retry_error_network" to "Network disconnected",
        "retry_error_rate_limit" to "Rate limit",
        "retry_error_server" to "Server error",
        "retry_error_server_overloaded" to "Server overloaded",
        "retry_error_ssl" to "SSL handshake failed",
        "retry_error_timeout" to "Connection timed out",
        "retry_error_unknown" to "Network error",
        "retry_error_with_code" to "%1${'s (%2$d)}s (%2${'d)}d)",
        "session_group_last_30_days" to "Last 30 Days",
        "session_group_last_7_days" to "Last 7 Days",
        "session_group_month_format" to "MMMM yyyy",
        "session_group_pinned" to "Pinned",
        "session_group_today" to "Today",
        "session_group_yesterday" to "Yesterday",
        "settings_about" to "About",
        "settings_add_mcp_server" to "Add MCP server",
        "settings_add_provider" to "Add vendor",
        "settings_agent_sound_subtitle" to "Pop a notification when agent finishes in background",
        "settings_agent_sound_title" to "Agent completion notification",
        "settings_app_permissions" to "App Permissions",
        "settings_approval_notification_subtitle" to "Notify when the AI requests permission, asks a question, or waits for plan approval",
        "settings_approval_notification_title" to "Approval notifications",
        "settings_auto_remove_stale_models" to "Remove stale models after fetching",
        "settings_auto_remove_stale_models_desc" to "On a successful fetch, drop local models the vendor no longer offers",
        "settings_autostart_subtitle" to "Allow the system to restart the app in background",
        "settings_autostart_title" to "Auto-start management",
        "settings_background_image" to "Background image",
        "settings_background_image_alpha" to "Opacity",
        "settings_background_image_none" to "None",
        "settings_background_image_pick" to "Pick image",
        "settings_background_image_remove" to "Remove background",
        "settings_backup" to "Backup & Restore",
        "settings_category_ai" to "AI Configuration",
        "settings_category_background" to "Background",
        "settings_category_data" to "Data & Diagnostics",
        "settings_category_environment" to "Environment",
        "settings_category_general" to "General",
        "settings_category_help" to "Help & About",
        "settings_category_permissions" to "Permissions & Background",
        "settings_category_system_permissions" to "System permissions",
        "settings_compaction_follow_chat" to "Follow current chat model",
        "settings_compaction_model" to "Compaction Model",
        "settings_compaction_threshold" to "Auto-compaction threshold",
        "settings_compaction_threshold_desc" to "Compact context once it reaches this share of the window",
        "settings_compaction_threshold_input_hint" to "Integer percentage between 1 and 100",
        "settings_container" to "Container & Images",
        "settings_default_models" to "Default Models",
        "settings_delete_external_workspace_sessions" to "Delete external workspace chats",
        "settings_delete_external_workspace_sessions_desc" to "Also delete a workspace's chats when an external local workspace is removed",
        "settings_enter_to_send" to "Enter key sends message",
        "settings_enter_to_send_desc" to "When off, Enter inserts a newline and you send with the button",
        "settings_first_byte_timeout" to "First byte timeout",
        "settings_first_byte_timeout_desc" to "Max wait for the first response chunk",
        "settings_general" to "Preferences",
        "settings_general_goal" to "Goal",
        "settings_general_input" to "Input",
        "settings_general_models" to "Models",
        "settings_general_network" to "Network",
        "settings_general_session" to "Session",
        "settings_general_tools" to "Tools",
        "settings_goal_max_tokens" to "Token budget",
        "settings_goal_max_tokens_desc" to "Cap on input+output tokens consumed by the autonomous loop",
        "settings_goal_max_tokens_input_hint" to "Integer not less than 1000",
        "settings_goal_max_turns" to "Max continuation turns",
        "settings_goal_max_turns_desc" to "Stop the autonomous loop after this many turns",
        "settings_goal_max_turns_input_hint" to "Integer number of turns greater than 0",
        "settings_image_gen_model" to "Image Generation Model",
        "settings_image_gen_unconfigured" to "Not configured",
        "settings_keepalive_subtitle" to "Foreground notification prevents background kill",
        "settings_keepalive_title" to "Background keepalive",
        "settings_language" to "Language",
        "settings_live_notification_subtitle" to "Show live progress notifications while the AI works in the background",
        "settings_live_notification_title" to "Live notifications",
        "settings_log" to "Log",
        "settings_log_no_files" to "No log files yet",
        "settings_log_read_failed" to "Failed to read log: %1${'s}s",
        "settings_max_network_retries" to "Retry attempts",
        "settings_max_network_retries_desc" to "Exponential-backoff retries after a network failure",
        "settings_mb_value" to "%1${'d MB}d MB",
        "settings_mcp" to "MCP Servers",
        "settings_models_fetch_failed" to "Fetch failed",
        "settings_network_guide_brand_huawei" to "Huawei / Honor: App launch → Manage manually → Auto-launch, Associated launch, Run in background",
        "settings_network_guide_brand_oppo" to "OPPO / OnePlus / realme: App management → Battery usage → Allow background activity; Battery → More settings → Optimize battery use → Don't optimize",
        "settings_network_guide_brand_samsung" to "Samsung: Battery and device care → Battery → Background usage limits → Never sleeping apps; Apps → Battery → Unrestricted",
        "settings_network_guide_brand_vivo" to "vivo / iQOO: Battery → Background power consumption management → Allow high background power; Apps and permissions → Auto-start",
        "settings_network_guide_brand_xiaomi" to "Xiaomi / Redmi: Battery and performance → App battery saver → No restrictions",
        "settings_network_guide_intro" to "If you see \"Network connection lost\" after switching to the background, set up in this order:",
        "settings_network_guide_more" to "See the online docs for more devices and details",
        "settings_network_guide_step_battery" to "3. In system settings, set this app's battery policy to \"Unrestricted / Don't optimize\" and allow auto-launch",
        "settings_network_guide_step_keepalive" to "1. Turn on \"Background keepalive\" above and keep its notification",
        "settings_network_guide_step_lock" to "2. Lock this app in the Recents screen instead of swiping it away",
        "settings_network_guide_subtitle" to "Connection dropping in background? Check these",
        "settings_network_guide_title" to "Background network troubleshooting",
        "settings_percent_value" to "%1${'d%%}d%%",
        "settings_permission_apk_install" to "Install unknown apps",
        "settings_permission_apk_install_desc" to "Allow installing APK packages",
        "settings_permission_battery" to "Ignore battery optimization",
        "settings_permission_battery_desc" to "Keep running in background to reduce process kill",
        "settings_permission_denied" to "Not allowed",
        "settings_permission_granted" to "Allowed",
        "settings_permission_notification_denied_desc" to "Background keepalive and agent completion notifications need the notification permission, but the system prompt cannot be shown. Please enable it manually in system settings",
        "settings_permission_notification_title" to "Notification permission required",
        "settings_permission_storage" to "Storage access",
        "settings_permission_storage_denied_desc" to "Storage permission was denied and can no longer be requested. Please enable it manually in system settings",
        "settings_permission_storage_desc" to "Read/write workspace files and container images",
        "settings_permissions" to "Tool Permissions",
        "settings_provider_editor" to "Vendor",
        "settings_providers" to "AI Vendors",
        "settings_reconnect" to "Reconnect",
        "settings_refresh_logs" to "Refresh logs",
        "settings_remote_servers" to "Connections & Sync",
        "settings_rerun_onboarding" to "Re-run onboarding",
        "settings_retries_count" to "%1${'d}d",
        "settings_retries_input_hint" to "0 disables retries",
        "settings_screen_on_subtitle" to "Screen stays awake while the app is in the foreground",
        "settings_screen_on_title" to "Keep screen on",
        "settings_select_section_hint" to "Pick a setting from the list",
        "settings_sendfile_max_size" to "sendFile max file size",
        "settings_sendfile_max_size_desc" to "Per-file size cap; exceeding it fails the whole batch",
        "settings_sendfile_max_size_input_hint" to "MB, an integer greater than 0",
        "settings_shizuku" to "Shizuku",
        "settings_shizuku_hint_denied" to "Tap to request Shizuku permission",
        "settings_shizuku_hint_not_installed" to "Tap to download and install Shizuku",
        "settings_shizuku_hint_not_running" to "Tap to open Shizuku and start the service",
        "settings_shizuku_hint_ready" to "Authorized — AI can run commands via adb shell",
        "settings_shizuku_status_denied" to "Not authorized",
        "settings_shizuku_status_not_installed" to "Not installed",
        "settings_shizuku_status_not_running" to "Service not running",
        "settings_shizuku_status_ready" to "Ready",
        "settings_skills" to "Skills",
        "settings_startup_session" to "Session on launch",
        "settings_startup_session_new" to "New session",
        "settings_startup_session_new_desc" to "Reuse an empty session, otherwise start a new one",
        "settings_startup_session_recent" to "Recent session",
        "settings_startup_session_recent_desc" to "Open the most recently updated session",
        "settings_storage" to "Storage",
        "settings_stream_idle_timeout" to "Chunk idle timeout",
        "settings_stream_idle_timeout_desc" to "Max wait between response chunks",
        "settings_subagent_tier_light" to "Light",
        "settings_subagent_tier_standard" to "Balanced",
        "settings_subagent_tier_strong" to "Strong",
        "settings_subagent_tier_unconfigured" to "Not configured",
        "settings_subagent_tiers_header" to "Subagent model tiers",
        "settings_subagents" to "Subagents",
        "settings_theme_title" to "Appearance",
        "settings_timeout_input_hint" to "Seconds; 0 means unlimited",
        "settings_timeout_seconds" to "%1${'d s}d s",
        "settings_timeout_unlimited" to "Unlimited",
        "settings_title" to "Settings",
        "settings_title_follow_chat" to "Follow current chat model",
        "settings_title_model" to "Title Model",
        "settings_token_stats_cache_hit_rate" to "Cache hit rate",
        "settings_token_stats_cache_read_write" to "Read %1${'s / Write %2$s}s / Write %2${'s}s",
        "settings_token_stats_calls_suffix" to "calls",
        "settings_token_stats_calls_suffix2" to "%1${'d calls}d calls",
        "settings_token_stats_col_cache_hit_rate" to "Hit rate",
        "settings_token_stats_col_cached_read" to "Cache read",
        "settings_token_stats_col_cached_write" to "Cache write",
        "settings_token_stats_col_cost" to "Cost",
        "settings_token_stats_col_duration" to "Duration",
        "settings_token_stats_col_input" to "Input",
        "settings_token_stats_col_model" to "Model",
        "settings_token_stats_col_output" to "Output",
        "settings_token_stats_col_provider" to "Vendor",
        "settings_token_stats_col_reasoning_effort" to "Effort",
        "settings_token_stats_col_reasoning_wire" to "Sent as",
        "settings_token_stats_col_status" to "Status",
        "settings_token_stats_col_time" to "Time",
        "settings_token_stats_col_ttfb" to "TTFB",
        "settings_token_stats_cost" to "Cost",
        "settings_token_stats_filter_all_models" to "All Models",
        "settings_token_stats_filter_all_providers" to "All Vendors",
        "settings_token_stats_filter_clear" to "Clear filters",
        "settings_token_stats_filter_model" to "Model: %1${'s}s",
        "settings_token_stats_filter_provider" to "Vendor: %1${'s}s",
        "settings_token_stats_legend_cached" to "Cached",
        "settings_token_stats_legend_input" to "Input",
        "settings_token_stats_legend_output" to "Output",
        "settings_token_stats_model" to "By model",
        "settings_token_stats_no_data" to "No data",
        "settings_token_stats_page" to "Page %1${'d of %2$d}d of %2${'d}d",
        "settings_token_stats_period_30d" to "30 days",
        "settings_token_stats_period_7d" to "7 days",
        "settings_token_stats_period_all" to "All",
        "settings_token_stats_period_today" to "Today",
        "settings_token_stats_provider" to "By vendor",
        "settings_token_stats_provider_keys" to "%1${'d keys}d keys",
        "settings_token_stats_recent_calls" to "Call records",
        "settings_token_stats_requests" to "Requests",
        "settings_token_stats_requests_unit" to "requests",
        "settings_token_stats_reset" to "Reset",
        "settings_token_stats_reset_confirm" to "All call records will be permanently deleted. Reset?",
        "settings_token_stats_reset_title" to "Reset token stats",
        "settings_token_stats_retries" to "%1${'d retries}d retries",
        "settings_token_stats_success_rate" to "Success rate %1${'s}s",
        "settings_token_stats_title" to "Token Stats",
        "settings_token_stats_total" to "Total",
        "settings_token_stats_trend" to "Usage trend",
        "settings_token_stats_unknown" to "Unknown",
        "settings_tokens_value" to "%1${'d}d",
        "settings_turns_value" to "%1${'d turns}d turns",
        "settings_user_guide" to "User Guide",
        "settings_vision_follow_chat" to "Follow current chat model",
        "settings_vision_model" to "Vision Model",
        "skills_add" to "Add skill",
        "skills_add_from_file" to "Import from file",
        "skills_add_from_file_desc" to "Pick a .md file with the instruction body",
        "skills_add_from_zip" to "Import from archive",
        "skills_add_from_zip_desc" to "Pick a .zip; may contain multiple skills",
        "skills_add_manual" to "Create manually",
        "skills_add_manual_desc" to "Fill in the name and content to create one from scratch",
        "skills_add_sheet_title" to "Add skill",
        "skills_batch_delete_confirm" to "Delete the selected %1${'d skill directories? This cannot be undone.}d skill directories? This cannot be undone.",
        "skills_batch_delete_title" to "Delete Skills",
        "skills_delete_confirm_message" to "This will delete the skill directory of \"%1${'s\". This action cannot be undone. Delete?}s\". This action cannot be undone. Delete?",
        "skills_delete_confirm_title" to "Delete Skill",
        "skills_description" to "Description",
        "skills_description_hint" to "When to use it; the AI decides whether to load it by this text",
        "skills_edit" to "Edit skill",
        "skills_editor_basic" to "Basics",
        "skills_editor_edit" to "Edit skill",
        "skills_editor_error_empty_instructions" to "The instructions cannot be empty",
        "skills_editor_error_invalid_name" to "Invalid name: it cannot be empty or contain path separators and other special characters",
        "skills_editor_error_io" to "Failed to write the skill file, please try again",
        "skills_editor_error_name_conflict" to "A skill with this name already exists in this scope",
        "skills_editor_new" to "New skill",
        "skills_editor_scope" to "Scope",
        "skills_empty" to "No skills installed",
        "skills_empty_hint" to "Tap + in the top bar to create or import one.",
        "skills_enable" to "Enable",
        "skills_import_error_empty_content" to "empty content",
        "skills_import_error_invalid_archive" to "cannot read the archive; make sure it is a valid .zip",
        "skills_import_error_invalid_name" to "invalid name",
        "skills_import_error_io" to "failed to write the skill file",
        "skills_import_error_name_conflict" to "a skill with the same name already exists",
        "skills_import_error_no_skill" to "no skill found in the archive",
        "skills_import_error_unsupported_file" to "unsupported file type",
        "skills_import_failure_line" to "%1${'s: %2$s}s: %2${'s}s",
        "skills_import_partial" to "Imported %1${'d, skipped %2$d}d, skipped %2${'d}d",
        "skills_import_result_title" to "Import result",
        "skills_import_running" to "Importing…",
        "skills_import_success" to "Imported %1${'d skill(s)}d skill(s)",
        "skills_instructions" to "Instructions",
        "skills_instructions_hint" to "Describe the standard workflow and caveats for this kind of task; Markdown supported",
        "skills_name_hint" to "Lowercase is recommended, e.g. pdf-report",
        "skills_no_global_skills" to "No global skills",
        "skills_no_project_skills" to "No skills for the current project",
        "skills_scope_global" to "Global",
        "skills_scope_project" to "This project",
        "skills_summary" to "Summary",
        "slash_command_compress_desc" to "Manually compact this session context",
        "slash_command_goal_desc" to "Set a session goal; the AI loops autonomously until done or the budget runs out",
        "slash_command_init_desc" to "Analyze the workspace codebase and generate or improve the AGENTS.md project rules",
        "slash_command_usage_desc" to "View today and all-time call count, token usage and estimated cost",
        "ssh_host_key_changed_title" to "SSH Host Key Changed",
        "ssh_host_key_confirm_title" to "Confirm SSH Host Key",
        "ssh_host_key_detail" to "Saved server host fingerprint:\n%1${'s}s",
        "ssh_host_key_empty" to "No saved host fingerprints",
        "ssh_host_key_fingerprint_value" to "Fingerprint: %1${'s}s",
        "ssh_host_key_reject" to "Reject",
        "ssh_host_key_remove" to "Remove fingerprint",
        "ssh_host_key_settings_desc" to "Confirmed host fingerprints help prevent man-in-the-middle attacks. Verify a server after reinstalling it or rotating its key before confirming again.",
        "ssh_host_key_settings_title" to "SSH Host Keys",
        "ssh_host_key_tab_fingerprints" to "Fingerprints",
        "ssh_host_key_tab_keys" to "Keys",
        "ssh_host_key_trust" to "Trust and connect",
        "ssh_login_key_add" to "Add key",
        "ssh_login_key_add_from_file" to "Import from file",
        "ssh_login_key_add_from_file_desc" to "Pick a private key file on this device",
        "ssh_login_key_add_from_paste" to "Paste key",
        "ssh_login_key_add_from_paste_desc" to "Paste the private key content directly",
        "ssh_login_key_add_method_title" to "Add login key",
        "ssh_login_key_content_label" to "Private key",
        "ssh_login_key_edit_title" to "Edit key",
        "ssh_login_key_empty" to "No saved login keys",
        "ssh_login_key_encrypted" to "Encrypted (passphrase required)",
        "ssh_login_key_fingerprint_label" to "Public key fingerprint",
        "ssh_login_key_name_label" to "Name",
        "ssh_login_key_passphrase_placeholder" to "Leave blank if the key is not encrypted",
        "ssh_login_key_paste_content_placeholder" to "-----BEGIN OPENSSH PRIVATE KEY-----",
        "ssh_login_key_paste_invalid" to "Invalid private key content, please check and retry",
        "ssh_login_key_paste_name_placeholder" to "Leave blank to name automatically",
        "ssh_login_key_paste_title" to "Paste key",
        "ssh_login_key_toggle_content" to "Show or hide the private key",
        "status_connected" to "Connected",
        "status_connection_failed" to "Connection Failed",
        "status_disconnected" to "Disconnected",
        "status_reconnecting" to "Reconnecting",
        "storage_breakdown_note" to "Tap a category with an arrow to expand details. Delete sessions to shrink chat history; reset or remove containers and workspaces from their own pages. External folder workspaces live on device storage and are not counted.",
        "storage_category_ai_config" to "AI config & output",
        "storage_category_apk" to "App package",
        "storage_category_caches" to "Caches",
        "storage_category_chat" to "Chat history",
        "storage_category_checkpoints" to "Checkpoints",
        "storage_category_container" to "Containers",
        "storage_category_container_images" to "Container images",
        "storage_category_logs" to "Logs",
        "storage_category_other" to "Other data",
        "storage_category_workspaces" to "Workspaces",
        "storage_chat_messages" to "%1${'d messages}d messages",
        "storage_clean" to "Clean",
        "storage_clean_caches" to "Cached files",
        "storage_clean_caches_desc" to "Temporary files, rebuilt automatically",
        "storage_clean_confirm_selected" to "The selected items below will be deleted (about %1${'s). This cannot be undone.}s). This cannot be undone.",
        "storage_clean_confirm_title" to "Confirm cleanup",
        "storage_clean_logs" to "Log files",
        "storage_clean_logs_desc" to "App and AI request logs; deleting them affects nothing",
        "storage_clean_scope" to "Only selected local app caches, logs and data in global directories are cleaned. This may affect historical output access or image follow-ups across multiple workspaces. Chat history, workspace files, containers, AI configuration and remote server data are not deleted.",
        "storage_clean_selected" to "Clean selected · %1${'s}s",
        "storage_clean_tool_output" to "Tool output archive",
        "storage_clean_tool_output_desc" to "Saved copies of oversized tool output; output paths in older messages stop resolving",
        "storage_clean_vision" to "Vision session cache",
        "storage_clean_vision_desc" to "History for image follow-up questions; old sessions can no longer be continued",
        "storage_device_free" to "%1${'s free of %2$s}s free of %2${'s}s",
        "storage_freed" to "Freed %1${'s}s",
        "storage_logs_ai" to "AI request logs",
        "storage_logs_app" to "App logs",
        "storage_refresh" to "Rescan",
        "storage_scanning" to "Calculating…",
        "storage_section_breakdown" to "Breakdown",
        "storage_total_title" to "Used by AiCode",
        "storage_untitled_session" to "Untitled session",
        "storage_workspace_attachments" to "incl. attachments %1${'s}s",
        "subagent_add" to "New subagent",
        "subagent_all_tools" to "All tools except task",
        "subagent_card_changed_files" to "%1${'d files changed}d files changed",
        "subagent_card_collapse" to "Collapse summary",
        "subagent_card_default_agent" to "Subagent",
        "subagent_card_expand" to "Show full summary",
        "subagent_card_open" to "Open session",
        "subagent_card_status_completed" to "Completed",
        "subagent_card_status_dispatched" to "Dispatched",
        "subagent_card_status_failed" to "Failed",
        "subagent_card_status_running" to "Running",
        "subagent_card_status_stopped" to "Stopped",
        "subagent_card_stop" to "Stop",
        "subagent_description_hint" to "When to dispatch it; the main session picks agents by this text",
        "subagent_dispatch_count" to "%1${'d tasks dispatched}d tasks dispatched",
        "subagent_dispatch_title" to "Subagent Tasks",
        "subagent_edit" to "Edit subagent",
        "subagent_editor_basic" to "Basics",
        "subagent_editor_clear" to "Clear",
        "subagent_editor_description" to "Description",
        "subagent_editor_edit" to "Edit subagent",
        "subagent_editor_effort" to "Reasoning effort",
        "subagent_editor_error_empty_prompt" to "The agent prompt cannot be empty",
        "subagent_editor_error_invalid_name" to "Invalid name: it cannot be empty or contain path separators and other special characters",
        "subagent_editor_error_io" to "Failed to write the definition file, please try again",
        "subagent_editor_error_name_conflict" to "A subagent with this name already exists in this scope",
        "subagent_editor_mode" to "Default mode",
        "subagent_editor_model_group" to "Model",
        "subagent_editor_new" to "New subagent",
        "subagent_editor_none" to "None",
        "subagent_editor_scope" to "Scope",
        "subagent_editor_selected_count" to "%1${'d selected}d selected",
        "subagent_editor_tools_allow" to "Allowed tools",
        "subagent_editor_tools_allow_hint" to "Leave empty to allow every tool except task",
        "subagent_editor_tools_deny" to "Denied tools",
        "subagent_editor_tools_deny_hint" to "Takes precedence over the allow list",
        "subagent_editor_tools_search" to "Search tools",
        "subagent_enable" to "Enable this subagent",
        "subagent_enable_hint" to "When off, the AI can no longer dispatch it; running subagents are unaffected",
        "subagent_inherit_parent" to "Inherit from parent session",
        "subagent_inject" to "Injected prompts",
        "subagent_inject_base" to "Subagent baseline",
        "subagent_inject_base_hint" to "Tool usage and path conventions, lightweight",
        "subagent_inject_main_rules" to "Full common rules",
        "subagent_inject_main_rules_hint" to "Pick either this or the subagent baseline; enabling both injects overlapping rules",
        "subagent_inject_memory" to "Memory summaries",
        "subagent_inject_memory_hint" to "Injects global and project long-term memories",
        "subagent_inject_none" to "No extra prompt injected",
        "subagent_inject_project_rules" to "Project rules",
        "subagent_inject_project_rules_hint" to "Injects AGENTS.md or CLAUDE.md",
        "subagent_inject_skills" to "Skill list",
        "subagent_inject_skills_hint" to "Lets it load and run project skills",
        "subagent_model" to "Model",
        "subagent_name_hint" to "Lowercase is recommended, e.g. researcher",
        "subagent_no_models" to "No models available. Add and configure a model under AI Vendors first.",
        "subagent_prompt" to "Agent prompt",
        "subagent_prompt_hint" to "Describe its role, how it should work and what to deliver",
        "subagent_scope_global" to "Global",
        "subagent_scope_project" to "This project",
        "subagent_source_file" to "Definition file",
        "subagent_tab_sessions" to "Chats",
        "subagent_tools" to "Tools",
        "subagent_tools_allow" to "Allowed only: %1${'s}s",
        "subagent_tools_deny" to "Denied: %1${'s}s",
        "subagents_batch_delete_confirm" to "Delete the selected %1${'d subagent definition files? This cannot be undone.}d subagent definition files? This cannot be undone.",
        "subagents_batch_delete_title" to "Delete Subagents",
        "subagents_delete_confirm_message" to "This deletes the definition file of \"%1${'s\" and cannot be undone. Delete it?}s\" and cannot be undone. Delete it?",
        "subagents_delete_confirm_title" to "Delete subagent",
        "subagents_empty" to "No custom subagents yet",
        "subagents_empty_hint" to "Tap + in the top bar to create one, or drop an agent's .md definition into ~/.aicode/agents/",
        "subagents_no_global" to "No global subagents",
        "subagents_no_project" to "No subagents in this project",
        "sync_batch_size_desc" to "Operations exceeding this limit are queued and processed in batches to avoid disconnects.",
        "sync_clear_index" to "Clear Sync Record",
        "sync_connect_and_sync" to "Connect and Sync",
        "sync_disconnect" to "Disconnect",
        "sync_download_all" to "Download All",
        "sync_follow_gitignore" to "Follow .gitignore rules",
        "sync_gitignore_desc" to "Automatically reads the project root .gitignore and ignores its contents.",
        "sync_max_batch_count" to "Max Count",
        "sync_max_batch_size" to "Max Sync Batch Size",
        "sync_path_mapping" to "%1${'s → %2$s}s → %2${'s}s",
        "sync_save_settings" to "Save Settings",
        "sync_saved" to "Sync settings saved",
        "sync_settings_title" to "Sync Settings",
        "sync_unknown_connection" to "Unknown connection",
        "sync_upload_changes" to "Upload Changes",
        "sync_upload_full" to "Force Full Upload",
        "terminal_cleaning_old_container" to "Cleaning up old container data…\n%1${'s items removed}s items removed",
        "terminal_close_tab" to "Close Tab",
        "terminal_cursor_bar" to "Bar",
        "terminal_cursor_block" to "Block",
        "terminal_cursor_style_title" to "Cursor Style",
        "terminal_cursor_underline" to "Underline",
        "terminal_extracting_env" to "Extracting environment…\n%1${'s files processed}s files processed",
        "terminal_font_builtin" to "JetBrains Mono",
        "terminal_font_default" to "System Monospace",
        "terminal_font_delete" to "Delete this font",
        "terminal_font_import" to "Import",
        "terminal_font_import_failed" to "Import failed. Please pick a valid ttf / otf / ttc font file",
        "terminal_font_size_title" to "Font Size",
        "terminal_font_title" to "Terminal Font",
        "terminal_installing_packages" to "Installing python3 / git / pip / node / npm…\n%1${'s}s",
        "terminal_no_open_tabs" to "No open terminal tabs",
        "terminal_prepare_error_unknown" to "Unknown error",
        "terminal_preparing_env_failed" to "Preparing container environment…\n%1${'s}s",
        "terminal_preparing_env_first_run" to "Preparing container environment…\nFirst run will extract the container, please wait",
        "terminal_preview_text" to "Terminal Preview",
        "terminal_retry" to "Retry",
        "terminal_settings_title" to "Terminal Settings",
        "terminal_start_failed" to "Cannot start terminal:\n%1${'s}s",
        "terminal_theme_dracula" to "Dracula",
        "terminal_theme_follow_app" to "Follow App",
        "terminal_theme_github_dark" to "GitHub Dark",
        "terminal_theme_github_light" to "GitHub Light",
        "terminal_theme_monokai" to "Monokai",
        "terminal_theme_one_dark" to "One Dark",
        "terminal_theme_termius_dark" to "Termius Dark",
        "terminal_theme_title" to "Color Theme",
        "terminal_title" to "Terminal",
        "theme_auto" to "Auto",
        "theme_dark" to "Dark",
        "theme_dynamic_color" to "Dynamic color",
        "theme_dynamic_color_desc" to "Derive colors from your wallpaper. Overrides the color theme above.",
        "theme_dynamic_color_unsupported" to "Requires Android 12 or newer",
        "theme_light" to "Light",
        "theme_mode_section" to "Appearance mode",
        "theme_preset_coffee" to "Coffee",
        "theme_preset_default" to "Default Blue",
        "theme_preset_forest" to "Forest",
        "theme_preset_graphite" to "Graphite",
        "theme_preset_section" to "Color theme",
        "title_no_models" to "No models available. Please add and configure a model in \"AI Vendors\" before selecting a dedicated title model.",
        "todo_completed" to "Completed",
        "todo_dashboard_all_done" to "All done",
        "todo_dashboard_progress" to "%1${'d/%2$d done}d/%2${'d done}d done",
        "todo_dashboard_title" to "Tasks",
        "todo_in_progress" to "In Progress",
        "token_stats_hour_suffix" to "h",
        "tool_browser_condition_met" to "Condition met.",
        "tool_browser_screenshot_failed" to "A screenshot is currently unavailable. Try again later, or use getText/getHtml to read the page.",
        "tool_browser_wait_timeout" to "Timed out before the condition was met.",
        "tool_changed_files_summary" to "+%1${'d −%2$d · %3$d files}d −%2${'d · %3$d files}d · %3${'d files}d files",
        "tool_command_rule_denied" to "Permission rules prohibit this command.",
        "tool_command_rule_denied_detail" to "Permission rules prohibit this command. Matched rule: %1${'s; offending segment: %2$s; suggestion: rewrite it in an allowed form, or ask the user to update the rule under Tool permissions and retry.}s; offending segment: %2${'s; suggestion: rewrite it in an allowed form, or ask the user to update the rule under Tool permissions and retry.}s; suggestion: rewrite it in an allowed form, or ask the user to update the rule under Tool permissions and retry.",
        "tool_command_unanalyzable_reason" to "The command contains constructs that cannot be statically analyzed (%1${'s); it can only be allowed once and cannot be remembered.}s); it can only be allowed once and cannot be remembered.",
        "tool_expand_remaining" to "Expand remaining %1${'s lines}s lines",
        "tool_instruction" to "Instruction",
        "tool_mcp_invalid_name" to "MCP server names may contain only ASCII letters, digits, underscores, and hyphens. Current value: %1${'s.}s.",
        "tool_mcp_stdio_added" to "Added local MCP server %2${'s (%1$s). The configuration takes effect in the next session; install any required runtime separately.}s (%1${'s). The configuration takes effect in the next session; install any required runtime separately.}s). The configuration takes effect in the next session; install any required runtime separately.",
        "tool_memory_saved" to "Saved memory “%1${'s” (%2$s). Its summary will be available in later sessions; use read for the full text.}s” (%2${'s). Its summary will be available in later sessions; use read for the full text.}s). Its summary will be available in later sessions; use read for the full text.",
        "tool_output_omitted" to "[Omitted %1${'d characters from the middle. %2$s]}d characters from the middle. %2${'s]}s]",
        "tool_output_saved_hint" to "Full output saved to %1${'s. Use readFile to read it in sections.}s. Use readFile to read it in sections.",
        "tool_output_unsaved_hint" to "Saving the full output failed. Only the beginning and end are available; try again with a smaller output range.",
        "tool_permission_rule_denied" to "Permission rules prohibit this tool call.",
        "tool_plan_mode_denied" to "This operation was not executed because PLAN mode allows read-only exploration. To implement changes, submit the plan and request to exit PLAN.",
        "tool_result" to "Result",
        "tool_shizuku_confirmation_reason" to "Shizuku operates on Android as adb shell. Each call requires confirmation and cannot be saved as always allowed.",
        "tool_subagent_message_delivered" to "Message delivered. A running subagent will receive it shortly; an idle subagent will resume to process it.",
        "tool_subagent_plan_readonly_warning" to "PLAN mode is active: subagents inherit read-only permissions, so file edits and commands will be blocked. If implementation is needed, request to exit PLAN before delegating.",
        "tool_subagent_provider_unresolved" to "The provider \"%1${'s\" in this subagent definition was not found (missing or name mismatch); the parent session provider will be combined with the definition model \"%2$s\", which may not be callable.}s\" in this subagent definition was not found (missing or name mismatch); the parent session provider will be combined with the definition model \"%2${'s\", which may not be callable.}s\", which may not be callable.",
        "tool_subagent_tier_fallback" to "The requested \"%1${'s\" tier is not configured or its provider is unavailable; using the \"%2$s\" tier (%3$s) instead.}s\" tier is not configured or its provider is unavailable; using the \"%2${'s\" tier (%3$s) instead.}s\" tier (%3${'s) instead.}s) instead.",
        "tool_subagent_tier_pinned" to "This subagent pins its model (%1${'s), so the requested \"%2$s\" tier was ignored.}s), so the requested \"%2${'s\" tier was ignored.}s\" tier was ignored.",
        "tool_subagent_tier_unavailable" to "The requested \"%1${'s\" tier is not configured or its provider is unavailable; this subagent will inherit the current session model.}s\" tier is not configured or its provider is unavailable; this subagent will inherit the current session model.",
        "tool_vision_default_multiple" to "Describe the key information in each of these %1${'d images in order, then compare their similarities and differences. Note anything unclear.}d images in order, then compare their similarities and differences. Note anything unclear.",
        "tool_vision_default_single" to "Describe the key information in the image, including legible text, elements, and layout. Note anything unclear.",
        "tool_vision_empty_response" to "The vision model returned no analysis.",
        "tool_vision_focus" to "Focus: %1${'s}s",
        "tool_vision_images_attached" to "Attached %1${'d images for direct viewing and analysis.}d images for direct viewing and analysis.",
        "update_channel_latest" to "Preview",
        "update_channel_latest_desc" to "Also detects pre-releases (RC)",
        "update_channel_stable" to "Stable",
        "update_channel_stable_desc" to "Stable releases only",
        "vision_no_models" to "No models available. Please add and configure a model in \"AI Vendors\" before selecting a vision model.",
        "web_search_results_count" to "%1${'s results found}s results found",
        "web_search_title" to "Web Search",
        "web_search_warning" to "Warning: %1${'s}s",
        "workbench_split_handle" to "Drag to resize panes",
        "workspace_add_external" to "Add External Workspace",
        "workspace_confirm" to "Confirm",
        "workspace_delete" to "Delete Workspace",
        "workspace_delete_confirm" to "Delete \"%1${'s\"? All files in this directory and this workspace's chats will be deleted and cannot be undone.}s\"? All files in this directory and this workspace's chats will be deleted and cannot be undone.",
        "workspace_empty_hint" to "No workspaces, tap %1${'s to create one}s to create one",
        "workspace_external_added" to "External workspace \"%1${'s\" added}s\" added",
        "workspace_external_exists" to "This directory is already a workspace",
        "workspace_external_invalid" to "The selection is not a valid directory",
        "workspace_external_remove_confirm" to "Remove workspace \"%1${'s\"? Only the link is removed; the directory and its files will not be deleted.}s\"? Only the link is removed; the directory and its files will not be deleted.",
        "workspace_external_remove_confirm_sessions" to "Remove workspace \"%1${'s\"? Only the link is removed; the directory and its files are kept, but this workspace's chats will be deleted.}s\"? Only the link is removed; the directory and its files are kept, but this workspace's chats will be deleted.",
        "workspace_external_unavailable_hint" to "This directory is currently unavailable (may have been moved or deleted). You can remove the record",
        "workspace_external_unresolvable" to "Could not resolve the selected directory. Pick from the \"Internal storage\" or \"SD card\" root; other file providers are not supported",
        "workspace_external_unsupported_path" to "The directory path contains the unsupported character \":\"",
        "workspace_external_unwritable" to "This directory is not writable. Grant storage permission in system settings and try again",
        "workspace_external_warning_continue" to "Continue",
        "workspace_external_warning_dont_show_again" to "Don’t show again",
        "workspace_external_warning_message" to "AI directly reads and writes the real files in the selected directory. Public storage does not support symlinks, so npm, yarn, pnpm, and git operations involving symlinks may fail. Removing the workspace only removes the association and does not delete directory contents.",
        "workspace_external_warning_title" to "Add External Workspace",
        "workspace_name_exists" to "A workspace with this name already exists",
        "workspace_name_hint" to "e.g. my-project",
        "workspace_new_internal" to "New Internal Workspace",
        "workspace_new_workspace" to "New Workspace",
        "workspace_open" to "Open Workspace",
        "workspace_remote_default_create_failed" to "Failed to create the default remote workspace. Check server permissions and disk space.",
        "workspace_remote_root_create_failed" to "Failed to create the remote workspace directory. Check the path and server permissions.",
        "workspace_select" to "Select Workspace",
        "workspace_switch" to "Switch Workspace",
        "workspace_switch_confirm" to "Switching workspace will stop all running AI sessions and close terminal tabs in the current workspace. Continue?",
        "workspace_unavailable_current" to "Workspace \"%1${'s\" is currently unavailable (the directory may have been moved or deleted)}s\" is currently unavailable (the directory may have been moved or deleted)",
        "workspace_unavailable_fallback" to "Workspace \"%1${'s\" is unavailable; switched to \"%2$s\"}s\" is unavailable; switched to \"%2${'s\"}s\"",
    )
}
