#!/bin/bash
set -e

echo "=========================================="
echo " SecureVault - EC2 Automated Setup Script"
echo "=========================================="

# 1. Update and install dependencies
echo "[1/6] Installing dependencies (Java 17, Node.js, Nginx, PostgreSQL)..."
sudo apt-get update -y
sudo apt-get install -y openjdk-17-jdk nginx postgresql postgresql-contrib curl maven

# Install Node.js (v20)
if ! command -v node &> /dev/null; then
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt-get install -y nodejs
fi

# 2. Setup PostgreSQL
echo "[2/6] Configuring PostgreSQL Database..."
sudo -u postgres psql -c "CREATE DATABASE securevault;" || true
sudo -u postgres psql -c "CREATE USER securevault_user WITH ENCRYPTED PASSWORD 'securevault_pass';" || true
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE securevault TO securevault_user;" || true

# 3. Build Backend
echo "[3/6] Building Spring Boot Backend..."
cd securevault-backend
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/securevault
export SPRING_DATASOURCE_USERNAME=securevault_user
export SPRING_DATASOURCE_PASSWORD=securevault_pass
mvn clean package -DskipTests
cd ..

# 4. Build Frontend
echo "[4/6] Building React Frontend..."
cd frontend
npm install
npm run build
cd ..

# 5. Setup Systemd Service for Backend
echo "[5/6] Setting up Backend Service..."
sudo bash -c 'cat > /etc/systemd/system/securevault.service << EOF
[Unit]
Description=SecureVault Spring Boot Application
After=network.target postgresql.service

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/SecureVault/securevault-backend
ExecStart=/usr/bin/java -jar target/securevault-backend-0.0.1-SNAPSHOT.jar
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/securevault"
Environment="SPRING_DATASOURCE_USERNAME=securevault_user"
Environment="SPRING_DATASOURCE_PASSWORD=securevault_pass"
Environment="JWT_SECRET=SecureVaultJwtSecretKey2024MustBeAtLeast32Bytes!"
Environment="ENCRYPTION_KEY=SecureVaultEncryptionKey2026ABCD"
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF'

sudo systemctl daemon-reload
sudo systemctl enable securevault
sudo systemctl restart securevault

# 6. Configure Nginx
echo "[6/6] Configuring Nginx Reverse Proxy..."
sudo bash -c 'cat > /etc/nginx/sites-available/securevault << EOF
server {
    listen 80;
    server_name _;

    root /home/ubuntu/SecureVault/frontend/dist;
    index index.html;

    # React App
    location / {
        try_files \$uri \$uri/ /index.html;
    }

    # Spring Boot API
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF'

sudo ln -sf /etc/nginx/sites-available/securevault /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo systemctl restart nginx

echo "=========================================="
echo " Deployment Complete! "
echo " Your application is now running on port 80."
echo "=========================================="
