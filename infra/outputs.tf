output "servicebus_queue_id" {
  value = azurerm_servicebus_queue.payment_events.id
}
output "container_app_id" {
  value = azurerm_container_app.intake.id
}
output "key_vault_id" {
  value = azurerm_key_vault.main.id
}
output "application_insights_connection_string" {
  value     = azurerm_application_insights.main.connection_string
  sensitive = true
}
