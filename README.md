# StressPilot SMTP Plugin

SMTP and IMAP plugin for load testing email sending and delivery verification.

## Testing Setup

Run the testing infrastructure using Docker:

```bash
cd resources/stresspilot-smtp-plugin
docker-compose up -d
```

| Service | Port (SMTP) | Port (UI/IMAP) | Auth |
|---------|-------------|----------------|------|
| **MailHog** | 1025 | 8025 (Web) | None |
| **GreenMail** | 3025 | 3143 (IMAP) | admin / password |

## Usage (CURL Examples)

Replace `projectId=1` with your actual project ID. All commands below are single-line for Windows.

### 1. Send via MailHog (No Auth)

**PowerShell:**
```powershell
curl.exe -X POST "http://localhost:52000/api/v1/endpoints/execute-adhoc?projectId=1" -H "Content-Type: application/json" -d '{"type":"SMTP","url":"localhost:1025","httpMethod":"NONE","httpHeaders":{"from":"test@stresspilot.dev","to":"user@example.com","subject":"Hello MailHog","username":"any","password":"any"},"body":"SMTP testing works!"}'
```

**CMD:**
```cmd
curl -X POST "http://localhost:52000/api/v1/endpoints/execute-adhoc?projectId=1" -H "Content-Type: application/json" -d "{\"type\":\"SMTP\",\"url\":\"localhost:1025\",\"httpMethod\":\"NONE\",\"httpHeaders\":{\"from\":\"test@stresspilot.dev\",\"to\":\"user@example.com\",\"subject\":\"Hello MailHog\",\"username\":\"any\",\"password\":\"any\"},\"body\":\"SMTP testing works!\"}"
```

### 2. Send via GreenMail (With Auth)

**PowerShell:**
```powershell
curl.exe -X POST "http://localhost:52000/api/v1/endpoints/execute-adhoc?projectId=1" -H "Content-Type: application/json" -d '{"type":"SMTP","url":"localhost:3025","httpMethod":"NONE","httpHeaders":{"from":"admin@stresspilot.dev","to":"receiver@example.com","subject":"Secure Mail","username":"admin","password":"password"},"body":"Authenticated SMTP test"}'
```

**CMD:**
```cmd
curl -X POST "http://localhost:52000/api/v1/endpoints/execute-adhoc?projectId=1" -H "Content-Type: application/json" -d "{\"type\":\"SMTP\",\"url\":\"localhost:3025\",\"httpMethod\":\"NONE\",\"httpHeaders\":{\"from\":\"admin@stresspilot.dev\",\"to\":\"receiver@example.com\",\"subject\":\"Secure Mail\",\"username\":\"admin\",\"password\":\"password\"},\"body\":\"Authenticated SMTP test\"}"
```

### 3. Send via GreenMail (SMTPS / SSL)

**PowerShell:**
```powershell
curl.exe -X POST "http://localhost:52000/api/v1/endpoints/execute-adhoc?projectId=1" -H "Content-Type: application/json" -d '{"type":"SMTP","url":"localhost:3465","httpMethod":"SSL","httpHeaders":{"from":"admin@stresspilot.dev","to":"receiver@example.com","subject":"SSL Mail","username":"admin","password":"password"},"body":"SSL SMTP test"}'
```

## IMAP Verification (Coming Soon)

The plugin also supports IMAP for verifying that emails were actually delivered.
