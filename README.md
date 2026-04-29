 <p style="text-align: center;">                                                                                                                                                                            
    <img src="james-1024x1024.png" alt="James logo" width="300">
  </p>                                                                                                                                                                                          


# James 

James is a terminal-based (TUI) AI coding agent built entirely in Java.
It connects to Azure OpenAI and provides an interactive assistant that can read, search, edit, and create files in your codebase — all from the terminal.
James uses an evaluator-optimizer pattern to iteratively refine solutions for complex coding tasks, while handling simple requests directly.


## Stack

- TamboUI
- Azure OpenAI
- Spring Boot
- Spring AI
- Java 25

## Requirements

- Azure OpenAI Deployment like gpt-5

## Installation

create *env.properties* file from *env.properties.example* and fill:
AZURE_OPENAI_API_KEY
AZURE_OPENAI_ENDPOINT
AZURE_OPENAI_DEPLOYMENT_NAME

Or create env variables in your OS <br>
Mac/Linux:
```bash
export AZURE_OPENAI_API_KEY=<your-api-key>                                                                                                                                                  
export AZURE_OPENAI_ENDPOINT=<your-endpoint>                                                                                                                                                  
export AZURE_OPENAI_DEPLOYMENT_NAME=<your-deployment-name>
```

Windows

```powershell
$env:AZURE_OPENAI_API_KEY="<your-api-key>"
$env:AZURE_OPENAI_ENDPOINT="<your-endpoint>"                                                                                                                                                  
$env:AZURE_OPENAI_DEPLOYMENT_NAME="<your-deployment-name>"
```

## Build

### Requirements

- Maven
- JDK
- optional - GraalVm JDK - for native

#### JVM

``
mvn package
``

#### Native
``
mvn -Pnative native:compile
``






