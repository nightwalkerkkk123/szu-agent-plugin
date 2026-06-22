#!/usr/bin/env node
/**
 * szu-agent-mcp.js
 *
 * Standalone Model Context Protocol (MCP) server for szu-agent-plugin.
 *
 * This file is intentionally self-contained and does NOT depend on the
 * Java project's internal MCP implementation. It talks to the packaged
 * fat-jar through the public CLI surface:
 *
 *   java -jar <jar> mcp list          -> tools/list
 *   java -jar <jar> skill call <n>    -> tools/call
 *
 * Transport:
 *   --transport stdio   (default) JSON-RPC over stdin/stdout
 *   --transport sse     HTTP SSE + POST endpoint
 *
 * Usage:
 *   node szu-agent-mcp.js --jar /path/to/szu-agent-plugin.jar
 *   node szu-agent-mcp.js --transport sse --port 3000
 *
 * Environment:
 *   SZU_AGENT_JAR        path to szu-agent-plugin.jar
 *   SZU_AGENT_JAVA       java binary (default: java)
 *   SZU_AGENT_JAVA_OPTS  extra JVM options
 */

const { Server } = require("@modelcontextprotocol/sdk/server/index.js");
const { StdioServerTransport } = require("@modelcontextprotocol/sdk/server/stdio.js");
const { SSEServerTransport } = require("@modelcontextprotocol/sdk/server/sse.js");
const { CallToolRequestSchema, ListToolsRequestSchema } = require("@modelcontextprotocol/sdk/types.js");
const express = require("express");
const { spawn } = require("child_process");
const { existsSync } = require("fs");
const { resolve } = require("path");

const VERSION = "0.1.0";
const PROTOCOL_VERSION = "2024-11-05";

function parseArgs(argv) {
  const args = { transport: "stdio", port: 3000, jar: null };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--transport" || a === "-t") args.transport = argv[++i];
    else if (a === "--port" || a === "-p") args.port = parseInt(argv[++i], 10);
    else if (a === "--jar" || a === "-j") args.jar = argv[++i];
    else if (a === "--help" || a === "-h") {
      console.error(`Usage: node szu-agent-mcp.js [options]
Options:
  --jar, -j <path>        Path to szu-agent-plugin.jar
  --transport, -t <mode>  stdio (default) or sse
  --port, -p <number>     SSE port (default: 3000)
  --help, -h              Show this help`);
      process.exit(0);
    }
  }
  return args;
}

function findJar(args) {
  const candidates = [
    args.jar,
    process.env.SZU_AGENT_JAR,
    resolve(__dirname, "../../target/szu-agent-plugin.jar"),
    resolve(process.cwd(), "target/szu-agent-plugin.jar"),
  ].filter(Boolean);
  for (const p of candidates) {
    if (existsSync(p)) return p;
  }
  throw new Error(
    `Cannot find szu-agent-plugin.jar. Use --jar or set SZU_AGENT_JAR. Searched: ${candidates.join(", ")}`
  );
}

function javaBin() {
  return process.env.SZU_AGENT_JAVA || "java";
}

function javaOpts() {
  return (process.env.SZU_AGENT_JAVA_OPTS || "").split(/\s+/).filter(Boolean);
}

function runJar(jar, ...args) {
  return new Promise((resolve, reject) => {
    const child = spawn(javaBin(), [...javaOpts(), "-jar", jar, ...args], {
      stdio: ["ignore", "pipe", "pipe"],
    });
    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (d) => (stdout += d));
    child.stderr.on("data", (d) => (stderr += d));
    child.on("error", reject);
    child.on("close", (code) => {
      if (code !== 0) {
        reject(new Error(`Jar exited ${code}: ${stderr || stdout}`));
      } else {
        try {
          resolve(JSON.parse(stdout));
        } catch (e) {
          reject(new Error(`Invalid JSON from jar: ${stdout}. stderr: ${stderr}`));
        }
      }
    });
  });
}

async function listTools(jar) {
  return runJar(jar, "mcp", "list");
}

async function callTool(jar, name, arguments_) {
  const flat = flattenArguments(arguments_);
  const argFlags = Object.entries(flat).map(([k, v]) => `--args=${k}=${v}`);
  return runJar(jar, "skill", "call", name, ...argFlags);
}

function flattenArguments(obj, prefix = "", out = {}) {
  if (obj === null || obj === undefined) return out;
  if (typeof obj !== "object") {
    out[prefix.replace(/\.$/, "")] = String(obj);
    return out;
  }
  if (Array.isArray(obj)) {
    out[prefix.replace(/\.$/, "")] = JSON.stringify(obj);
    return out;
  }
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k;
    flattenArguments(v, key + ".", out);
  }
  // Remove trailing dots introduced by recursion
  const cleaned = {};
  for (const [k, v] of Object.entries(out)) {
    cleaned[k.replace(/\.$/, "")] = v;
  }
  return cleaned;
}

function buildServer(jar) {
  const server = new Server(
    { name: "szu-agent-mcp", version: VERSION },
    { capabilities: { tools: { listChanged: false } } }
  );

  server.setRequestHandler(ListToolsRequestSchema, async () => {
    const tools = await listTools(jar);
    // The jar returns { schemaVersion, tools: [...] }; adapt to MCP shape.
    return {
      tools: tools.tools.map((t) => ({
        name: t.name,
        description: t.description,
        inputSchema: t.inputSchema || { type: "object", additionalProperties: true },
      })),
    };
  });

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const { name, arguments: args } = request.params;
    const result = await callTool(jar, name, args || {});
    const text = JSON.stringify(result, null, 2);
    return {
      content: [{ type: "text", text }],
      isError: !result.success,
    };
  });

  return server;
}

async function serveStdio(jar) {
  const server = buildServer(jar);
  const transport = new StdioServerTransport();
  console.error("Connecting to stdio transport...");
  await server.connect(transport);
  console.error("Connected. Waiting for requests...");
  // Keep alive until stdin closes.
  process.stdin.on("close", () => {
    console.error("Stdin closed.");
    server.close().catch(() => {});
  });
}

async function serveSse(jar, port) {
  const app = express();
  const sessions = new Map();

  app.get("/sse", async (req, res) => {
    const transport = new SSEServerTransport("/message", res);
    const server = buildServer(jar);
    sessions.set(transport.sessionId, { server, transport });
    transport.onclose = () => sessions.delete(transport.sessionId);
    await server.connect(transport);
  });

  app.post("/message", async (req, res) => {
    const sessionId = req.query.sessionId;
    const session = sessions.get(sessionId);
    if (!session) {
      res.status(404).send("Session not found");
      return;
    }
    await session.transport.handlePostMessage(req, res);
  });

  app.get("/health", (_req, res) => {
    res.json({ ok: true, jar, sessions: sessions.size });
  });

  return new Promise((resolve) => {
    const httpServer = app.listen(port, () => {
      console.error(`szu-agent-mcp SSE server listening on http://localhost:${port}`);
      console.error(`  SSE endpoint:  http://localhost:${port}/sse`);
      console.error(`  POST endpoint: http://localhost:${port}/message?sessionId=<id>`);
      console.error(`  Health check:  http://localhost:${port}/health`);
      resolve(httpServer);
    });
  });
}

async function main() {
  const args = parseArgs(process.argv);
  const jar = findJar(args);

  if (args.transport === "stdio") {
    await serveStdio(jar);
  } else if (args.transport === "sse") {
    await serveSse(jar, args.port);
  } else {
    throw new Error(`Unknown transport: ${args.transport}`);
  }
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
