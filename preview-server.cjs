#!/usr/bin/env node
// Minimal static server for the Lovable preview.
// Accepts --port <n> (supervisor appends it).
const http = require("http");
const fs = require("fs");
const path = require("path");

const args = process.argv.slice(2);
let port = 8080;
for (let i = 0; i < args.length; i++) {
  if (args[i] === "--port" && args[i + 1]) port = parseInt(args[i + 1], 10);
}

const ROOT = path.join(__dirname, "public");
const server = http.createServer((req, res) => {
  let file = decodeURIComponent((req.url || "/").split("?")[0]);
  if (file === "/" || file === "") file = "/index.html";
  const full = path.join(ROOT, file);
  fs.readFile(full, (err, data) => {
    if (err) {
      res.writeHead(404, { "content-type": "text/plain" });
      res.end("404");
      return;
    }
    const ext = path.extname(full).toLowerCase();
    const type =
      ext === ".html" ? "text/html; charset=utf-8"
      : ext === ".css" ? "text/css"
      : ext === ".js" ? "application/javascript"
      : ext === ".png" ? "image/png"
      : ext === ".jpg" || ext === ".jpeg" ? "image/jpeg"
      : "application/octet-stream";
    res.writeHead(200, { "content-type": type });
    res.end(data);
  });
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Preview serving on http://localhost:${port}`);
});
