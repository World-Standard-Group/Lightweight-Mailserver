#!/bin/bash
set -euo pipefail

CONFIG_FILE="/etc/mailctl/config.yaml"
TEMP_DIR="/tmp/mailctl-config-$(date +%s)"
LOG_FILE="/var/log/mailctl-deploy.log"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

log "Starting mailctl configuration deployment..."

# Check config exists
if [[ ! -f "$CONFIG_FILE" ]]; then
    log "ERROR: Config file not found: $CONFIG_FILE"
    exit 1
fi

# Generate configuration
log "Generating configuration to $TEMP_DIR..."
mkdir -p "$TEMP_DIR"
if ! mailctl --config "$CONFIG_FILE" config generate --output-dir "$TEMP_DIR" >> "$LOG_FILE" 2>&1; then
    log "ERROR: Configuration generation failed"
    exit 1
fi

# Validate configuration
log "Validating configuration..."
if ! mailctl --config "$CONFIG_FILE" config validate --config-dir "$TEMP_DIR" >> "$LOG_FILE" 2>&1; then
    log "ERROR: Configuration validation failed"
    exit 1
fi

# Deploy configuration
log "Deploying configuration..."
if ! mailctl --config "$CONFIG_FILE" config deploy --config-dir "$TEMP_DIR" >> "$LOG_FILE" 2>&1; then
    log "ERROR: Configuration deployment failed"
    exit 1
fi

# Reload services
log "Reloading services..."
mailctl --config "$CONFIG_FILE" service reload all >> "$LOG_FILE" 2>&1

# Cleanup
rm -rf "$TEMP_DIR"

log "Configuration deployment completed successfully"

# Run health check
log "Running health check..."
mailctl --config "$CONFIG_FILE" health check >> "$LOG_FILE" 2>&1 || true

log "Deployment finished"