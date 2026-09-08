#!/usr/bin/env bash
# ==============================================================================
# WatchTogether — Oracle Cloud VM 1-Click Deployment Script
# Tested on Oracle Linux / Ubuntu 22.04 LTS / Debian 12 (ARM64 & x86_64)
# ==============================================================================

set -e

echo "🚀 [1/5] Updating system packages..."
sudo apt-get update -y && sudo apt-get upgrade -y || sudo yum update -y

echo "📦 [2/5] Installing Docker and Docker Compose..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    sudo usermod -aG docker $USER
    rm get-docker.sh
fi

if ! docker compose version &> /dev/null; then
    sudo apt-get install -y docker-compose-plugin || sudo yum install -y docker-compose-plugin
fi

echo "🛡️ [3/5] Configuring Firewall for Ports 80, 443, 8080..."
if command -v ufw &> /dev/null; then
    sudo ufw allow 80/tcp
    sudo ufw allow 443/tcp
    sudo ufw allow 8080/tcp
    sudo ufw reload || true
elif command -v firewall-cmd &> /dev/null; then
    sudo firewall-cmd --zone=public --add-port=80/tcp --permanent || true
    sudo firewall-cmd --zone=public --add-port=443/tcp --permanent || true
    sudo firewall-cmd --zone=public --add-port=8080/tcp --permanent || true
    sudo firewall-cmd --reload || true
fi

# Fix Oracle Cloud iptables rules that often block incoming traffic
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT || true
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT || true
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 8080 -j ACCEPT || true
sudo netfilter-persistent save || true

echo "🐳 [4/5] Building and starting WatchTogether containers..."
sudo docker compose down || true
sudo docker compose up -d --build

echo "✅ [5/5] Deployment Finished!"
echo "Server is now running live on port 80 and 8080."
echo "Test health: curl http://localhost:8080/health"
