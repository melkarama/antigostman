# Antig - Advanced Java HTTP Client

**Antig** is a powerful, lightweight, and native Java Swing application designed to emulate the core functionality of Postman. It provides a robust environment for testing, documenting, and executing HTTP requests with a focus on performance and developer productivity.

## 🚀 Features

### 📡 Request Execution
*   **Methods**: Full support for `GET`, `POST`, `PUT`, `DELETE`, and `PATCH`.
*   **Protocols**: Switch between **HTTP/1.1** and **HTTP/2**.
*   **Body Types**: Support for `JSON`, `XML`, `TEXT`, and `x-www-form-urlencoded` payloads.
*   **Timeout**: Configurable request timeout.
*   **Download Mode**: Check **"DL Content"** to automatically download the response as a file. The app detects the file type (via Apache Tika) and opens it with your default system viewer.

### ⚡ Scripting & Automation
*   **JavaScript Engine**: Built-in support for **Prescript** (before request) and **Postscript** (after response) execution using the Nashorn/JS engine.
*   **Variables**:
    *   **Environment Variables**: Define variables at the Request, Folder, or Collection level.
    *   **Global Variables**: Project-wide variables accessible everywhere.
    *   **Variable Interpolation**: Use `{{variableName}}` syntax in URLs, Headers, and Body.
*   **Console**: Integrated debug console (`console.log`) to view script outputs and execution logs.

### 📊 Response Analysis
*   **Dual View**: Separate tabs for Request and Response.
*   **Syntax Highlighting**: Beautiful syntax highlighting for JSON, XML, HTML, and Properties.
*   **Formatted Preview**: Auto-formatting (Pretty Print) for JSON and XML responses.

### 📝 Reporting & Sharing
*   **PDF Reports**: Generate detailed execution reports including request/response headers, bodies, and logs.
*   **Email Integration**: One-click button to generate a PDF and attach it to a new email (supports Outlook on Windows and Thunderbird on Linux).

### 🛠 Productivity & UI
*   **Themes**: Modern UI with support for **Light** and **Dark** modes (powered by FlatLaf).
*   **Project Management**: Save and load projects as XML files.
*   **Recent Projects**: Quick access to recently opened projects.
*   **Tree View**: Organize requests into nested Folders and Collections.
*   **Drag & Drop**: Reorder requests and folders easily.

## ⌨️ Shortcuts

| Shortcut | Action |
| :--- | :--- |
| `Ctrl + Enter` | **Execute Request** (Send) |
| `Ctrl + S` | **Save Project** |
| `F2` | **Rename** selected node |
| `F3` | **Clone** selected node |
| `Delete` | **Delete** selected node |

## 🛠️ Technology Stack

*   **Language**: Java 21
*   **UI Framework**: Swing
*   **Networking**: `java.net.http.HttpClient`
*   **Build Tool**: Maven
*   **Key Libraries**:
    *   `FlatLaf` (UI Themes)
    *   `RSyntaxTextArea` (Code Editor)
    *   `Jackson` (JSON Processing)
    *   `Apache Tika` (MIME Type Detection)
    *   `OpenPDF` (PDF Generation)
    *   `Lombok` (Boilerplate reduction)

## 📦 Installation & Usage

### Prerequisites
*   Java JDK 21 or higher
*   Maven

### Build
Clone the repository and build using Maven:

```bash
git clone https://github.com/yourusername/antig.git
cd antig
mvn clean package
```

### Run
You can run the application using the generated JAR file:

```bash
java -jar target/antig-1.2-SNAPSHOT.jar
```

Alternatively, on Windows, a `run-app.bat` script is generated in the `target` folder.

## 📁 Project Structure

The project saves data in a custom XML structure designed to prevent cyclic dependencies while maintaining the hierarchy of your collections.

*   **Logs**: Execution logs are saved in `.log` files alongside your project XML file.
*   **Temp Files**: Downloaded content and generated PDF reports are stored in the system's temporary directory (`/tmp` or `%TEMP%`).

## 📄 License
[MIT License](LICENSE)
