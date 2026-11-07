# ServerPilot

A lightweight web app to connect and monitor your Linux servers via SSH, right from the browser.

---

## What it does

- **Connect servers** using a password or SSH private key
- **Test the connection** before saving any credentials
- **View live metrics** - CPU, RAM, and Disk usage, plus OS info and uptime
- **Manage multiple servers** from a single dashboard
- **Processes & Services** - view running processes and manage system services (start, stop, restart)
- Metrics auto-refresh every 30 seconds

---

## Project Structure

```
server-pilot/
├── server-pilot-backend/     # Spring Boot REST API (Java 21)
└── server-pilot-frontend/    # React + Vite + Tailwind CSS
```

---

## Tech Stack

| Layer    | Technology                                      |
|----------|-------------------------------------------------|
| Frontend | React 19, Vite 8, Tailwind CSS 4, React Router |
| Backend  | Spring Boot, Java 21, Maven                     |
| Database | MySQL (via Spring Data JPA)                     |
| SSH      | JSch (mwiede fork)                              |
| Auth     | SSH credential-based (no app login required)    |

---

## Getting Started

### Prerequisites

- Java 21+
- Node.js 20+
- MySQL database

---

### Backend Setup

1. **Create a MySQL database**

2. **Set environment variables** before running:

   ```bash
   export DB_URL=jdbc:mysql://localhost:3306/your_db
   export DB_USERNAME=your_db_user
   export DB_PASSWORD=your_db_password
   export ALLOWED_ORIGIN=http://localhost:5173
   ```

3. **Run the backend:**

   ```bash
   cd server-pilot-backend
   ./mvnw spring-boot:run
   ```

   The API will start on `http://localhost:8080`.

---

### Frontend Setup

1. **Install dependencies:**

   ```bash
   cd server-pilot-frontend
   npm install
   ```

2. **Start the dev server:**

   ```bash
   npm run dev
   ```

   Open `http://localhost:5173` in your browser.

---

## API Endpoints

| Method | Endpoint                   | What it does                          |
|--------|----------------------------|---------------------------------------|
| GET    | `/api/servers`             | List all saved servers with status    |
| POST   | `/api/servers`             | Add a new server                      |
| DELETE | `/api/servers/{id}`        | Remove a server                       |
| GET    | `/api/servers/{id}/metrics`| Fetch live metrics for a server       |
| POST   | `/api/servers/connect`     | Test SSH connection (doesn't save)    |

---

## Roadmap

Features planned for upcoming releases:

- **Filesystem Browser** - Browse your server's directory tree, view file contents, and manage files over SSH.

---

## How the SSH Connection Works

When you add a server, ServerPilot:

1. Opens an SSH session using your credentials (password or PEM key).
2. Runs a set of shell commands to collect CPU, memory, disk, network, and OS info.
3. Parses the output and sends it back as JSON to the frontend.

---

## Author

**Karthick Nathan** [@karthicknathan](https://github.com/karthicknathan-2003)
